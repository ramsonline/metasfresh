package de.metas.banking.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.compiere.model.I_C_BankStatement;
import org.compiere.model.I_C_BankStatementLine;
import org.compiere.model.MPeriod;
import org.compiere.model.X_C_DocType;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.TimeUtil;

import de.metas.acct.api.IFactAcctDAO;
import de.metas.banking.BankStatementId;
import de.metas.banking.BankStatementLineId;
import de.metas.banking.BankStatementLineReference;
import de.metas.banking.payment.IBankStatementPaymentBL;
import de.metas.document.engine.DocStatus;
import de.metas.document.engine.DocumentHandler;
import de.metas.document.engine.DocumentTableFields;
import de.metas.document.engine.IDocument;
import de.metas.i18n.IMsgBL;
import de.metas.i18n.ITranslatableString;
import de.metas.i18n.TranslatableStringBuilder;
import de.metas.i18n.TranslatableStrings;
import de.metas.payment.PaymentId;
import de.metas.payment.api.IPaymentBL;
import de.metas.util.Check;
import de.metas.util.Services;
import de.metas.util.StringUtils;

/*
 * #%L
 * de.metas.banking.base
 * %%
 * Copyright (C) 2020 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class BankStatementDocumentHandler implements DocumentHandler
{
	private final IBankStatementPaymentBL bankStatmentPaymentBL = Services.get(IBankStatementPaymentBL.class);
	private final IBankStatementDAO bankStatementDAO = Services.get(IBankStatementDAO.class);
	private final IBankStatementBL bankStatementBL = Services.get(IBankStatementBL.class);
	private final IPaymentBL paymentBL = Services.get(IPaymentBL.class);
	private final IFactAcctDAO factAcctDAO = Services.get(IFactAcctDAO.class);
	private final IMsgBL msgBL = Services.get(IMsgBL.class);

	private static I_C_BankStatement extractBankStatement(final DocumentTableFields docFields)
	{
		return InterfaceWrapperHelper.create(docFields, I_C_BankStatement.class);
	}

	@Override
	public String getSummary(final DocumentTableFields docFields)
	{
		final I_C_BankStatement bankStatement = extractBankStatement(docFields);
		return buildSummary(bankStatement)
				.translate(Env.getADLanguageOrBaseLanguage());
	}

	private ITranslatableString buildSummary(final I_C_BankStatement bankStatement)
	{
		final TranslatableStringBuilder summary = TranslatableStrings.builder()
				.append(bankStatement.getDocumentNo())
				.appendADElement("StatementDifference").append("=").append(bankStatement.getStatementDifference(), DisplayType.Amount);

		final String description = StringUtils.trimBlankToNull(bankStatement.getDescription());
		if (description != null)
		{
			summary.append(" - ").append(description);
		}

		return summary.build();
	}

	@Override
	public String getDocumentInfo(final DocumentTableFields docFields)
	{
		final I_C_BankStatement bankStatement = extractBankStatement(docFields);

		final StringBuilder documentInfo = new StringBuilder();

		final String bankAccountName = bankStatement.getC_BP_BankAccount().getA_Name();
		if (Check.isNotBlank(bankAccountName))
		{
			documentInfo.append(bankAccountName.trim());
		}

		if (documentInfo.length() > 0)
		{
			documentInfo.append(" ");
		}
		documentInfo.append(bankStatement.getDocumentNo());

		return documentInfo.toString();
	}

	@Override
	public int getDoc_User_ID(final DocumentTableFields docFields)
	{
		final I_C_BankStatement bankStatement = extractBankStatement(docFields);
		return bankStatement.getUpdatedBy();
	}

	@Override
	public LocalDate getDocumentDate(final DocumentTableFields docFields)
	{
		final I_C_BankStatement bankStatement = extractBankStatement(docFields);
		return TimeUtil.asLocalDate(bankStatement.getStatementDate());
	}

	@Override
	public BigDecimal getApprovalAmt(DocumentTableFields docFields)
	{
		final I_C_BankStatement bankStatement = extractBankStatement(docFields);
		return bankStatement.getStatementDifference();
	}

	@Override
	public void approveIt(final DocumentTableFields docFields)
	{
		final I_C_BankStatement bankStatement = extractBankStatement(docFields);
		approveIt(bankStatement);
	}

	private void approveIt(final I_C_BankStatement bankStatement)
	{
		bankStatement.setIsApproved(true);
	}

	@Override
	public void rejectIt(final DocumentTableFields docFields)
	{
		final I_C_BankStatement bankStatement = extractBankStatement(docFields);
		bankStatement.setIsApproved(false);
	}

	@Override
	public String prepareIt(final DocumentTableFields docFields)
	{
		final I_C_BankStatement bankStatement = extractBankStatement(docFields);

		// Std Period open?
		MPeriod.testPeriodOpen(Env.getCtx(), bankStatement.getStatementDate(), X_C_DocType.DOCBASETYPE_BankStatement, bankStatement.getAD_Org_ID());

		final BankStatementId bankStatementId = BankStatementId.ofRepoId(bankStatement.getC_BankStatement_ID());
		final List<I_C_BankStatementLine> lines = bankStatementBL.getLinesByBankStatementId(bankStatementId);
		if (lines.isEmpty())
		{
			throw new AdempiereException("@NoLines@");
		}
		// Lines
		BigDecimal total = BigDecimal.ZERO;
		Timestamp minDate = bankStatement.getStatementDate();
		Timestamp maxDate = minDate;
		for (final I_C_BankStatementLine line : lines)
		{
			total = total.add(line.getStmtAmt());
			if (line.getDateAcct().before(minDate))
			{
				minDate = line.getDateAcct();
			}
			if (line.getDateAcct().after(maxDate))
			{
				maxDate = line.getDateAcct();
			}

			if (line.isMultiplePaymentOrInvoice() && line.isMultiplePayment())
			{
				// Payment in C_BankStatementLine_Ref are mandatory
				final BankStatementLineId bankStatementLineId = BankStatementLineId.ofRepoId(line.getC_BankStatementLine_ID());
				for (final BankStatementLineReference refLine : bankStatementDAO.getLineReferences(bankStatementLineId))
				{
					if (refLine.getPaymentId() == null)
					{
						// TODO -> AD_Message
						throw new AdempiereException("Missing payment in reference line "
								+ refLine.getLineNo() + " of line "
								+ line.getLine());
					}
				}
			}
		}

		bankStatement.setStatementDifference(total);
		bankStatement.setEndingBalance(bankStatement.getBeginningBalance().add(total));
		MPeriod.testPeriodOpen(Env.getCtx(), minDate, X_C_DocType.DOCBASETYPE_BankStatement, 0);
		MPeriod.testPeriodOpen(Env.getCtx(), maxDate, X_C_DocType.DOCBASETYPE_BankStatement, 0);

		bankStatement.setDocAction(IDocument.ACTION_Complete);
		return IDocument.STATUS_InProgress;
	}

	@Override
	public String completeIt(final DocumentTableFields docFields)
	{
		final I_C_BankStatement bankStatement = extractBankStatement(docFields);

		// Implicit Approval
		if (!bankStatement.isApproved())
		{
			approveIt(bankStatement);
		}

		//
		final BankStatementId bankStatementId = BankStatementId.ofRepoId(bankStatement.getC_BankStatement_ID());
		final List<I_C_BankStatementLine> lines = bankStatementBL.getLinesByBankStatementId(bankStatementId);
		for (final I_C_BankStatementLine line : lines)
		{
			//
			// Cash/bank transfer
			if (line.getC_BP_BankAccountTo_ID() > 0)
			{
				final BankStatementLineId linkedBankStatementLineId = BankStatementLineId.ofRepoIdOrNull(line.getLink_BankStatementLine_ID());
				if (linkedBankStatementLineId != null)
				{
					final I_C_BankStatementLine lineFrom = bankStatementBL.getLineById(linkedBankStatementLineId);
					if (lineFrom.getLink_BankStatementLine_ID() > 0
							&& lineFrom.getLink_BankStatementLine_ID() != line.getC_BankStatementLine_ID())
					{
						throw new AdempiereException("Bank Statement Line is allocated to another line"); // TODO: translate
					}

					final boolean sameCurrency = lineFrom.getC_Currency_ID() == line.getC_Currency_ID();
					if (sameCurrency && lineFrom.getTrxAmt().negate().compareTo(line.getTrxAmt()) != 0)
					{
						throw new AdempiereException("Transfer amount differs"); // TODO: translate
					}

					lineFrom.setC_BP_BankAccountTo_ID(bankStatement.getC_BP_BankAccount_ID());
					lineFrom.setLink_BankStatementLine_ID(line.getC_BankStatementLine_ID());
					bankStatementDAO.save(lineFrom);
				}
			}

			bankStatmentPaymentBL.findOrCreateSinglePaymentAndLinkIfPossible(bankStatement, line);
		}

		//
		// Reconcile payments
		paymentBL.markReconciled(getAllPaymentIds(lines));

		//
		bankStatement.setProcessed(true);
		bankStatement.setDocAction(IDocument.ACTION_Close);
		return IDocument.STATUS_Completed;
	}

	private Set<PaymentId> getAllPaymentIds(final List<I_C_BankStatementLine> lines)
	{
		final ArrayList<BankStatementLineId> bankStatementLineIds = new ArrayList<>();
		final HashSet<PaymentId> paymentIds = new HashSet<>();

		for (final I_C_BankStatementLine line : lines)
		{
			final BankStatementLineId bankStatementLineId = BankStatementLineId.ofRepoId(line.getC_BankStatementLine_ID());
			bankStatementLineIds.add(bankStatementLineId);

			//
			// Collect payment from line
			final PaymentId paymentId = PaymentId.ofRepoIdOrNull(line.getC_Payment_ID());
			if (paymentId != null)
			{
				paymentIds.add(paymentId);
			}
		}

		//
		// Collect payments from bank statement line references
		paymentIds.addAll(bankStatementDAO
				.getLineReferences(bankStatementLineIds)
				.getPaymentIds());

		return paymentIds;
	}

	@Override
	public void voidIt(final DocumentTableFields docFields)
	{
		final I_C_BankStatement bankStatement = extractBankStatement(docFields);

		final DocStatus docStatus = DocStatus.ofNullableCodeOrUnknown(bankStatement.getDocStatus());
		if (docStatus.isClosedReversedOrVoided())
		{
			throw new AdempiereException("Document Closed: " + docStatus);
		}

		// Not Processed
		if (DocStatus.Drafted.equals(docStatus)
				|| DocStatus.Invalid.equals(docStatus)
				|| DocStatus.InProgress.equals(docStatus)
				|| DocStatus.Approved.equals(docStatus)
				|| DocStatus.NotApproved.equals(docStatus))
		{

			// Std Period open?
		}
		else
		{
			MPeriod.testPeriodOpen(Env.getCtx(), bankStatement.getStatementDate(), X_C_DocType.DOCBASETYPE_BankStatement, bankStatement.getAD_Org_ID());
			factAcctDAO.deleteForDocumentModel(bankStatement);
		}

		final BankStatementId bankStatementId = BankStatementId.ofRepoId(bankStatement.getC_BankStatement_ID());
		final List<I_C_BankStatementLine> lines = bankStatementBL.getLinesByBankStatementId(bankStatementId);

		bankStatementBL.unlinkPaymentsAndDeleteReferences(lines);

		//
		// Set lines to 0
		for (final I_C_BankStatementLine line : lines)
		{
			if (line.getStmtAmt().signum() != 0)
			{
				final String description = buildVoidDescription(line);
				addDescription(line, description);
				//
				line.setStmtAmt(BigDecimal.ZERO);
				line.setTrxAmt(BigDecimal.ZERO);
				line.setChargeAmt(BigDecimal.ZERO);
				line.setInterestAmt(BigDecimal.ZERO);

				//
				// Cash/bank transfer
				final BankStatementLineId linkedBankStatementLineId = BankStatementLineId.ofRepoIdOrNull(line.getLink_BankStatementLine_ID());
				if (linkedBankStatementLineId != null)
				{
					final I_C_BankStatementLine lineFrom = bankStatementBL.getLineById(linkedBankStatementLineId);
					if (lineFrom.getLink_BankStatementLine_ID() == line.getC_BankStatementLine_ID())
					{
						lineFrom.setLink_BankStatementLine_ID(-1);
						bankStatementDAO.save(lineFrom);
					}
				}

				bankStatementDAO.save(line);
			}
		}

		addDescription(bankStatement, msgBL.getMsg(Env.getCtx(), "Voided"));
		bankStatement.setStatementDifference(BigDecimal.ZERO);

		bankStatement.setProcessed(true);
		bankStatement.setDocAction(IDocument.ACTION_None);
	}

	private String buildVoidDescription(final I_C_BankStatementLine line)
	{
		final Properties ctx = Env.getCtx();
		String description = msgBL.getMsg(ctx, "Voided") + " ("
				+ msgBL.translate(ctx, "StmtAmt") + "=" + line.getStmtAmt();
		if (line.getTrxAmt().signum() != 0)
		{
			description += ", " + msgBL.translate(ctx, "TrxAmt") + "=" + line.getTrxAmt();
		}
		if (line.getChargeAmt().signum() != 0)
		{
			description += ", " + msgBL.translate(ctx, "ChargeAmt") + "=" + line.getChargeAmt();
		}
		if (line.getInterestAmt().signum() != 0)
		{
			description += ", " + msgBL.translate(ctx, "InterestAmt") + "=" + line.getInterestAmt();
		}
		description += ")";
		return description;
	}

	@Override
	public void reactivateIt(final DocumentTableFields docFields)
	{
		throw new UnsupportedOperationException();
	}

	private static void addDescription(final I_C_BankStatement bankStatement, final String description)
	{
		final String desc = bankStatement.getDescription();
		if (desc == null)
		{
			bankStatement.setDescription(description);
		}
		else
		{
			bankStatement.setDescription(desc + " | " + description);
		}
	}

	private static void addDescription(final I_C_BankStatementLine line, final String description)
	{
		final String desc = line.getDescription();
		if (desc == null)
		{
			line.setDescription(description);
		}
		else
		{
			line.setDescription(desc + " | " + description);
		}
	}
}
