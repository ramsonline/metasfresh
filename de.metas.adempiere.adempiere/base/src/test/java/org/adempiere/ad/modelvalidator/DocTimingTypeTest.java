package org.adempiere.ad.modelvalidator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2016 metas GmbH
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

public class DocTimingTypeTest
{
	@Test
	public void test_valueOfTimingInt()
	{
		for (final DocTimingType timing : DocTimingType.values())
		{
			assertThat(DocTimingType.valueOf(timing.toInt())).isSameAs(timing);
		}
	}

	@Test
	public void test_forAction()
	{
		for (final DocTimingType timing : DocTimingType.values())
		{
			assertThat(DocTimingType.forAction(timing.getDocAction(), timing.getBeforeAfter())).isSameAs(timing);
		}
	}

	public void test_isDocAction()
	{
		for (final DocTimingType timing : DocTimingType.values())
		{
			assertThat(timing.isDocAction(timing.getDocAction())).isTrue();
		}
	}
}
