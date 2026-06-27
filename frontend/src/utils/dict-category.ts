import type { DictCategoryOption } from '@/types/dict-category'

export interface SelectOption {
  label: string
  value: string
}

export function mergeDictOptions(
  fallbackOptions: SelectOption[],
  dynamicOptions: DictCategoryOption[]
): SelectOption[] {
  if (!dynamicOptions.length) {
    return fallbackOptions
  }

  const merged: SelectOption[] = []
  const usedValues = new Set<string>()

  dynamicOptions.forEach((item) => {
    merged.push({
      label: item.dictName,
      value: item.value
    })
    usedValues.add(item.value)
  })

  fallbackOptions.forEach((item) => {
    if (!usedValues.has(item.value)) {
      merged.push(item)
    }
  })

  return merged
}

export function mapDictOptions(dynamicOptions: DictCategoryOption[]): SelectOption[] {
  return dynamicOptions.map((item) => ({
    label: item.dictName,
    value: item.value
  }))
}

export function buildActiveDictOptions(
  activeOptions: DictCategoryOption[],
  currentValue?: string | null,
  allOptions: DictCategoryOption[] = [],
  currentLabel?: string | null
): SelectOption[] {
  const mappedOptions = mapDictOptions(activeOptions)
  const normalizedValue = currentValue?.trim()

  if (!normalizedValue || mappedOptions.some((item) => item.value === normalizedValue)) {
    return mappedOptions
  }

  const historicalOption = allOptions.find((item) => item.value === normalizedValue)
  return [{
    label: historicalOption?.dictName || currentLabel?.trim() || normalizedValue,
    value: normalizedValue
  }, ...mappedOptions]
}

export function buildDictLabelMap(
  dynamicOptions: DictCategoryOption[],
  fallbackMap: Record<string, string> = {}
): Record<string, string> {
  return dynamicOptions.reduce<Record<string, string>>((acc, item) => {
    acc[item.value] = item.dictName
    return acc
  }, { ...fallbackMap })
}
