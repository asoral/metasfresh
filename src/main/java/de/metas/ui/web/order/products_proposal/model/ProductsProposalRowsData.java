package de.metas.ui.web.order.products_proposal.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.adempiere.util.lang.impl.TableRecordReferenceSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import de.metas.bpartner.BPartnerId;
import de.metas.lang.SOTrx;
import de.metas.order.OrderId;
import de.metas.pricing.PriceListVersionId;
import de.metas.product.ProductId;
import de.metas.ui.web.exceptions.EntityNotFoundException;
import de.metas.ui.web.order.products_proposal.filters.ProductsProposalViewFilter;
import de.metas.ui.web.order.products_proposal.model.ProductsProposalRowChangeRequest.RowUpdate;
import de.metas.ui.web.order.products_proposal.model.ProductsProposalRowChangeRequest.UserChange;
import de.metas.ui.web.view.AbstractCustomView.IEditableRowsData;
import de.metas.ui.web.view.IEditableView.RowEditingContext;
import de.metas.ui.web.window.datatypes.DocumentId;
import de.metas.ui.web.window.datatypes.DocumentIdIntSequence;
import de.metas.ui.web.window.datatypes.DocumentIdsSelection;
import de.metas.ui.web.window.datatypes.json.JSONDocumentChangedEvent;
import de.metas.util.GuavaCollectors;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2019 metas GmbH
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

public class ProductsProposalRowsData implements IEditableRowsData<ProductsProposalRow>
{
	private final DocumentIdIntSequence nextRowIdSequence;

	private ArrayList<DocumentId> rowIdsOrderedAndFiltered;
	private final ArrayList<DocumentId> rowIdsOrdered; // used to preserve the order
	private final HashMap<DocumentId, ProductsProposalRow> rowsById;

	@Getter
	private final Optional<PriceListVersionId> singlePriceListVersionId;
	@Getter
	private final Optional<PriceListVersionId> basePriceListVersionId;

	@Getter
	private final Optional<OrderId> orderId;
	@Getter
	private final Optional<BPartnerId> bpartnerId;
	@Getter
	private final SOTrx soTrx;

	private ProductsProposalViewFilter filter = ProductsProposalViewFilter.ANY;

	@Builder
	private ProductsProposalRowsData(
			@NonNull final DocumentIdIntSequence nextRowIdSequence,
			@NonNull final List<ProductsProposalRow> rows,
			@Nullable final PriceListVersionId singlePriceListVersionId,
			@Nullable final PriceListVersionId basePriceListVersionId,
			@Nullable final OrderId orderId,
			@Nullable final BPartnerId bpartnerId,
			@NonNull final SOTrx soTrx)
	{
		this.nextRowIdSequence = nextRowIdSequence;

		rowIdsOrdered = rows.stream()
				.map(ProductsProposalRow::getId)
				.collect(Collectors.toCollection(ArrayList::new));
		rowIdsOrderedAndFiltered = new ArrayList<>(rowIdsOrdered);

		rowsById = rows.stream()
				.collect(GuavaCollectors.toMapByKey(HashMap::new, ProductsProposalRow::getId));

		this.singlePriceListVersionId = Optional.ofNullable(singlePriceListVersionId);
		this.basePriceListVersionId = Optional.ofNullable(basePriceListVersionId);
		this.orderId = Optional.ofNullable(orderId);
		this.bpartnerId = Optional.ofNullable(bpartnerId);
		this.soTrx = soTrx;
	}

	@Override
	public synchronized int size()
	{
		return rowIdsOrderedAndFiltered.size();
	}

	@Override
	public Map<DocumentId, ProductsProposalRow> getDocumentId2AllRows()
	{
		return getDocumentId2TopLevelRows();
	}

	@Override
	public synchronized ImmutableMap<DocumentId, ProductsProposalRow> getDocumentId2TopLevelRows()
	{
		return rowIdsOrderedAndFiltered.stream()
				.map(rowsById::get)
				.collect(GuavaCollectors.toImmutableMapByKey(ProductsProposalRow::getId));
	}

	@Override
	public synchronized ImmutableList<ProductsProposalRow> getTopLevelRows()
	{
		return rowIdsOrderedAndFiltered.stream()
				.map(rowsById::get)
				.collect(ImmutableList.toImmutableList());
	}

	@Override
	public ImmutableList<ProductsProposalRow> getAllRows()
	{
		return getTopLevelRows();
	}

	@Override
	public DocumentIdsSelection getDocumentIdsToInvalidate(final TableRecordReferenceSet recordRefs)
	{
		return DocumentIdsSelection.EMPTY;
	}

	@Override
	public void invalidateAll()
	{
	}

	@Override
	public void patchRow(final RowEditingContext ctx, final List<JSONDocumentChangedEvent> fieldChangeRequests)
	{
		final UserChange request = ProductsProposalRowActions.toUserChangeRequest(fieldChangeRequests);
		changeRow(ctx.getRowId(), row -> ProductsProposalRowReducers.reduce(row, request));
	}

	public void patchRow(@NonNull final DocumentId rowId, @NonNull ProductsProposalRowChangeRequest request)
	{
		changeRow(rowId, row -> ProductsProposalRowReducers.reduce(row, request));
	}

	private synchronized void changeRow(@NonNull final DocumentId rowId, @NonNull final UnaryOperator<ProductsProposalRow> mapper)
	{
		if (!rowIdsOrderedAndFiltered.contains(rowId))
		{
			throw new EntityNotFoundException(rowId.toJson());
		}

		rowsById.compute(rowId, (key, oldRow) -> {
			if (oldRow == null)
			{
				throw new EntityNotFoundException(rowId.toJson());
			}

			return mapper.apply(oldRow);
		});
	}

	public synchronized Set<ProductId> getProductIds()
	{
		return rowsById.values()
				.stream()
				.map(ProductsProposalRow::getProductId)
				.collect(ImmutableSet.toImmutableSet());
	}

	private synchronized Optional<ProductsProposalRow> getRowByProductAndASI(@NonNull final ProductId productId, @NonNull final ProductASIDescription asiDescription)
	{
		return rowsById.values()
				.stream()
				.filter(row -> productId.equals(row.getProductId())
						&& asiDescription.equals(row.getAsiDescription()))
				.findFirst();
	}

	public void addOrUpdateRows(@NonNull final List<ProductsProposalRowAddRequest> requests)
	{
		requests.forEach(this::addOrUpdateRow);
	}

	private synchronized void addOrUpdateRow(@NonNull final ProductsProposalRowAddRequest request)
	{
		final ProductsProposalRow existingRow = getRowByProductAndASI(request.getProductId(), request.getAsiDescription())
				.orElse(null);
		if (existingRow != null)
		{
			patchRow(existingRow.getId(), RowUpdate.builder()
					.price(request.getPrice())
					.lastShipmentDays(request.getLastShipmentDays())
					.copiedFromProductPriceId(request.getCopiedFromProductPriceId())
					.build());
		}
		else
		{
			addRow(createRow(request));
		}
	}

	private ProductsProposalRow createRow(ProductsProposalRowAddRequest request)
	{
		return ProductsProposalRow.builder()
				.id(nextRowIdSequence.nextDocumentId())
				.product(request.getProduct())
				.asiDescription(request.getAsiDescription())
				.standardPrice(request.getPrice())
				.lastShipmentDays(request.getLastShipmentDays())
				.copiedFromProductPriceId(request.getCopiedFromProductPriceId())
				.build();
	}

	private synchronized void addRow(final ProductsProposalRow row)
	{
		rowIdsOrderedAndFiltered.add(0, row.getId()); // add first
		rowIdsOrdered.add(0, row.getId()); // add first

		rowsById.put(row.getId(), row);
	}

	public synchronized void removeRowsByIds(@NonNull final Set<DocumentId> rowIds)
	{
		rowIdsOrderedAndFiltered.removeAll(rowIds);
		rowIdsOrdered.removeAll(rowIds);
		rowIds.forEach(rowsById::remove);
	}

	public synchronized ProductsProposalViewFilter getFilter()
	{
		return filter;
	}

	public synchronized void filter(@NonNull final ProductsProposalViewFilter filter)
	{
		if (Objects.equals(this.filter, filter))
		{
			return;
		}

		this.filter = filter;

		rowIdsOrderedAndFiltered = rowIdsOrdered
				.stream()
				.filter(rowId -> rowsById.get(rowId).isMatching(filter))
				.collect(Collectors.toCollection(ArrayList::new));
	}
}
