package de.metas.ui.web.order.sales.pricingConditions.view;

import java.awt.Color;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.Services;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_M_DiscountSchemaBreak;
import org.compiere.model.I_M_PricingSystem;
import org.compiere.model.I_M_Product;
import org.compiere.util.CCache;
import org.compiere.util.Evaluatees;

import de.metas.bpartner.BPartnerId;
import de.metas.order.IOrderLinePricingConditions;
import de.metas.payment.paymentterm.PaymentTermId;
import de.metas.pricing.PricingSystemId;
import de.metas.pricing.conditions.PriceOverrideType;
import de.metas.product.ProductId;
import de.metas.ui.web.window.datatypes.ColorValue;
import de.metas.ui.web.window.datatypes.LookupValue;
import de.metas.ui.web.window.datatypes.LookupValuesList;
import de.metas.ui.web.window.model.lookup.LookupDataSource;
import de.metas.ui.web.window.model.lookup.LookupDataSourceFactory;
import de.metas.util.IColorRepository;
import de.metas.util.MFColor;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2018 metas GmbH
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

public class PricingConditionsRowLookups
{
	public static PricingConditionsRowLookups newInstance()
	{
		return new PricingConditionsRowLookups();
	}

	private final LookupDataSource bpartnerLookup;
	private final LookupDataSource productLookup;
	private final LookupDataSource priceTypeLookup;
	private final LookupDataSource pricingSystemLookup;
	private final LookupDataSource paymentTermLookup;

	private CCache<Integer, String> temporaryPriceConditionsColorCache = CCache.newCache("temporaryPriceConditionsColor", 1, CCache.EXPIREMINUTES_Never);

	private PricingConditionsRowLookups()
	{
		final LookupDataSourceFactory lookupFactory = LookupDataSourceFactory.instance;
		bpartnerLookup = lookupFactory.searchInTableLookup(I_C_BPartner.Table_Name);
		productLookup = lookupFactory.searchInTableLookup(I_M_Product.Table_Name);
		priceTypeLookup = lookupFactory.listByAD_Reference_Value_ID(PriceOverrideType.AD_Reference_ID);
		pricingSystemLookup = lookupFactory.searchInTableLookup(I_M_PricingSystem.Table_Name);
		paymentTermLookup = lookupFactory.searchByColumn(I_M_DiscountSchemaBreak.Table_Name, I_M_DiscountSchemaBreak.COLUMNNAME_C_PaymentTerm_ID);
	}

	public LookupValue lookupBPartner(final BPartnerId bpartnerId)
	{
		if (bpartnerId == null)
		{
			return null;
		}
		return bpartnerLookup.findById(bpartnerId.getRepoId());
	}

	public LookupValue lookupProduct(final ProductId productId)
	{
		if (productId == null)
		{
			return null;
		}
		return productLookup.findById(productId.getRepoId());
	}

	public LookupValue lookupPriceType(@NonNull final PriceOverrideType priceType)
	{
		return priceTypeLookup.findById(priceType.getCode());
	}

	public LookupValue lookupPricingSystem(final PricingSystemId pricingSystemId)
	{
		if (pricingSystemId == null)
		{
			return null;
		}
		return pricingSystemLookup.findById(pricingSystemId.getRepoId());
	}

	public LookupValue lookupPaymentTerm(final PaymentTermId paymentTermId)
	{
		if (paymentTermId == null)
		{
			return null;
		}
		return paymentTermLookup.findById(paymentTermId.getRepoId());
	}

	public LookupValuesList getFieldTypeahead(final String fieldName, final String query)
	{
		return getLookupDataSource(fieldName).findEntities(Evaluatees.empty(), query);
	}

	public LookupValuesList getFieldDropdown(final String fieldName)
	{
		return getLookupDataSource(fieldName).findEntities(Evaluatees.empty(), 20);
	}

	private LookupDataSource getLookupDataSource(final String fieldName)
	{
		if (PricingConditionsRow.FIELDNAME_PaymentTerm.equals(fieldName))
		{
			return paymentTermLookup;
		}
		else if (PricingConditionsRow.FIELDNAME_PriceType.equals(fieldName))
		{
			return priceTypeLookup;
		}
		else if (PricingConditionsRow.FIELDNAME_BasePricingSystem.equals(fieldName))
		{
			return pricingSystemLookup;
		}
		else
		{
			throw new AdempiereException("Field " + fieldName + " does not exist or it's not a lookup field");
		}
	}

	public String getTemporaryPriceConditionsColor()
	{
		return temporaryPriceConditionsColorCache.getOrLoad(0, this::retrieveTemporaryPriceConditionsColor);
	}

	private String retrieveTemporaryPriceConditionsColor()
	{
		final int temporaryPriceConditionsColorId = Services.get(IOrderLinePricingConditions.class).getTemporaryPriceConditionsColorId();
		return toHexString(Services.get(IColorRepository.class).getColorById(temporaryPriceConditionsColorId));
	}

	private static final String toHexString(final MFColor color)
	{
		if (color == null)
		{
			return null;
		}

		final Color awtColor = color.toFlatColor().getFlatColor();
		return ColorValue.toHexString(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());
	}

}
