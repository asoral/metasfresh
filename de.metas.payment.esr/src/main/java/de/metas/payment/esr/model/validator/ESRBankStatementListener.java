package de.metas.payment.esr.model.validator;

import de.metas.banking.model.BankStatementLineReferenceList;
import de.metas.banking.service.IBankStatementListener;
import de.metas.payment.esr.api.IESRImportBL;
import lombok.NonNull;

/*
 * #%L
 * de.metas.payment.esr
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

/**
 * Listens to bank statement events and manages the relation with ESR.
 *
 * @author metas-dev <dev@metasfresh.com>
 *
 */
class ESRBankStatementListener implements IBankStatementListener
{
	private final IESRImportBL esrImportBL;

	public ESRBankStatementListener(@NonNull final IESRImportBL esrImportBL)
	{
		this.esrImportBL = esrImportBL;
	}

	@Override
	public void onBeforeDeleteBankStatementLineReferences(@NonNull final BankStatementLineReferenceList lineRefs)
	{
		esrImportBL.unlinkESRImportLinesFromBankStatement(lineRefs.getBankStatementLineIds());
	}
}
