export interface AggregateBatchOption {
  batch_no: string
  quantity: number
}

export interface AggregateValidationRow {
  warehouseId: number | null
  locationId: number | null
  materialId: number | null
  materialName: string
  batchNo: string
  quantity: number | null
  _batchNos?: AggregateBatchOption[]
}

export interface AggregateBatchViolation {
  materialName: string
}

const buildAggregateKey = (row: AggregateValidationRow) => [
  row.warehouseId,
  row.locationId,
  row.materialId,
  row.batchNo,
].join('::')

export const findAggregateBatchStockViolation = (
  rows: AggregateValidationRow[]
): AggregateBatchViolation | null => {
  const aggregates = new Map<string, { requested: number; row: AggregateValidationRow }>()

  for (const row of rows) {
    if (!row.batchNo || row.quantity == null) continue

    const key = buildAggregateKey(row)
    const existing = aggregates.get(key)
    if (existing) {
      existing.requested += row.quantity
      continue
    }

    aggregates.set(key, {
      requested: row.quantity,
      row,
    })
  }

  for (const { requested, row } of aggregates.values()) {
    const batch = row._batchNos?.find((option) => option.batch_no === row.batchNo)
    if (batch && requested > batch.quantity) {
      return { materialName: row.materialName }
    }
  }

  return null
}
