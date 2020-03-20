/**
 * 
 */
package de.metas.banking.callout;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.DBException;
import org.compiere.model.I_C_BankStatementLine;
import org.compiere.util.DB;
import org.compiere.util.Env;

import com.google.common.base.MoreObjects;

import de.metas.currency.ICurrencyBL;
import de.metas.money.CurrencyId;
import de.metas.util.Services;
import de.metas.util.time.SystemTime;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.Value;

/*
 * #%L
 * de.metas.banking.base
 * %%
 * Copyright (C) 2017 metas GmbH
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
 * @author metas-dev <dev@metasfresh.com>
 *
 */
public class BankStatementLineOrRefHelper
{
	final static private ICurrencyBL currencyConversionBL = Services.get(ICurrencyBL.class);

	public static void setBankStatementLineOrRefFieldsWhenInvoiceChanged(@NonNull final I_C_BankStatementLine line)
	{
		setBankStatementLineOrRefAmountsToZero(line);
		setBankStatementLineOrRefTrxAndStmtAmountsToZero(line);

		final InvoiceInfoVO invoiceInfo = fetchInvoiceCurrencyBpartnerAndAmounts(line);
		setBankStatementLineOrRefCurrencyBPartneAndInvoiceWhenInvoiceChanged(line, invoiceInfo);
		setBankStatementLineOrRefAmountsWhenInvoiceChanged(line, invoiceInfo);
	}

	public static void setBankStatementLineOrRefAmountsToZero(@NonNull final I_C_BankStatementLine line)
	{
		line.setDiscountAmt(BigDecimal.ZERO);
		line.setWriteOffAmt(BigDecimal.ZERO);
		line.setOverUnderAmt(BigDecimal.ZERO);
		line.setIsOverUnderPayment(false);
	}

	private static void setBankStatementLineOrRefTrxAndStmtAmountsToZero(@NonNull final I_C_BankStatementLine line)
	{
		line.setTrxAmt(BigDecimal.ZERO);
		line.setStmtAmt(BigDecimal.ZERO);
	}

	private static InvoiceInfoVO fetchInvoiceCurrencyBpartnerAndAmounts(@NonNull final I_C_BankStatementLine line)
	{
		final Timestamp dateTrx = getTrxDate(line);
		final int invoiceId = line.getC_Invoice_ID();
		final int invoicePayScheduleId = getC_InvoicePaySchedule_ID(Env.getCtx(), invoiceId);
		return fetchInvoiceInfo(invoiceId, invoicePayScheduleId, dateTrx);
	}

	private static void setBankStatementLineOrRefCurrencyBPartneAndInvoiceWhenInvoiceChanged(@NonNull final I_C_BankStatementLine line, final InvoiceInfoVO invoiceInfo)
	{
		if (invoiceInfo == null)
		{
			return;
		}

		line.setC_BPartner_ID(invoiceInfo.getBpartnerId());
		line.setC_Currency_ID(CurrencyId.toRepoId(invoiceInfo.getCurrencyId()));
		line.setC_Invoice_ID(invoiceInfo.getInvoiceId());
	}

	private static void setBankStatementLineOrRefAmountsWhenInvoiceChanged(@NonNull final I_C_BankStatementLine line, final InvoiceInfoVO invoiceInfo)
	{
		if (invoiceInfo == null)
		{
			return;
		}

		final BigDecimal openAmount = invoiceInfo.getOpenAmt();
		final BigDecimal discount = invoiceInfo.getDiscountAmt();
		final BigDecimal openAmtActual = openAmount.subtract(discount);
		line.setTrxAmt(openAmtActual);
		line.setDiscountAmt(invoiceInfo.getDiscountAmt());
		line.setStmtAmt(openAmtActual);
	}

	private static int getC_InvoicePaySchedule_ID(Properties ctx, int C_Invoice_ID)
	{
		int C_InvoicePaySchedule_ID = 0;
		if (Env.getContextAsInt(ctx, Env.WINDOW_INFO, Env.TAB_INFO, "C_Invoice_ID") == C_Invoice_ID
				&& Env.getContextAsInt(ctx, Env.WINDOW_INFO, Env.TAB_INFO, "C_InvoicePaySchedule_ID") != 0)
		{
			C_InvoicePaySchedule_ID = Env.getContextAsInt(ctx, Env.WINDOW_INFO, Env.TAB_INFO, "C_InvoicePaySchedule_ID");
		}
		return C_InvoicePaySchedule_ID;
	}

	@Builder
	@Value
	private static class InvoiceInfoVO
	{
		private final CurrencyId currencyId;
		private final int bpartnerId;
		private final int invoiceId;
		private final BigDecimal openAmt;
		private final BigDecimal discountAmt;
	}

	private static InvoiceInfoVO fetchInvoiceInfo(int invoiceId, int invoicePayScheduleId, Timestamp dateTrx)
	{
		if (invoiceId <= 0)
		{
			return null;
		}

		final PreparedStatementParamsForInvoice params = PreparedStatementParamsForInvoice.builder()
				.dateTrx(dateTrx)
				.invoicePayScheduleId(invoicePayScheduleId)
				.invoiceId(invoiceId)
				.build();

		try (PreparedStatement pstmt = createPreparedStatementForInvoice(params);
				ResultSet rs = pstmt.executeQuery())
		{
			if (rs.next())
			{
				return InvoiceInfoVO.builder()
						.invoiceId(invoiceId)
						.bpartnerId(rs.getInt(1))
						.currencyId(CurrencyId.ofRepoId(rs.getInt(2)))
						.openAmt(MoreObjects.firstNonNull(rs.getBigDecimal(3), BigDecimal.ZERO))
						.discountAmt(MoreObjects.firstNonNull(rs.getBigDecimal(4), BigDecimal.ZERO))
						.build();
			}
		}
		catch (SQLException e)
		{
			throw new DBException(e);
		}

		return null;

	}

	@Builder
	@Value
	private static class PreparedStatementParamsForInvoice
	{
		private final int invoicePayScheduleId;
		private final int invoiceId;
		private final Timestamp dateTrx;
	}

	private static PreparedStatement createPreparedStatementForInvoice(PreparedStatementParamsForInvoice params) throws SQLException
	{
		final StringBuilder sql = new StringBuilder()
				.append("SELECT C_BPartner_ID,C_Currency_ID,")
				.append(" invoiceOpen(C_Invoice_ID, ?),")
				.append(" invoiceDiscount(C_Invoice_ID,?,?), IsSOTrx ")
				.append("FROM C_Invoice WHERE C_Invoice_ID=?");

		final PreparedStatement pstmt = DB.prepareStatement(sql.toString(), ITrx.TRXNAME_None);
		pstmt.setInt(1, params.getInvoicePayScheduleId());
		pstmt.setTimestamp(2, params.getDateTrx());
		pstmt.setInt(3, params.getInvoicePayScheduleId());
		pstmt.setInt(4, params.getInvoiceId());
		return pstmt;
	}

	@Data
	@Builder
	private static class Amounts
	{
		private BigDecimal invoiceOpenAmt;
		private BigDecimal discountAmt;
		private BigDecimal writeOffAmt;
		private BigDecimal overUnderAmt;
		private BigDecimal payAmt;
	}

	private static Timestamp getTrxDate(@NonNull final I_C_BankStatementLine line)
	{
		return MoreObjects.firstNonNull(line.getStatementLineDate(), SystemTime.asTimestamp());
	}
}
