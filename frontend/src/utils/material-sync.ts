const MATERIAL_UPDATED_EVENT = 'smartfood:material-updated'

export const dispatchMaterialUpdated = () => {
  window.dispatchEvent(new CustomEvent(MATERIAL_UPDATED_EVENT))
}

export const onMaterialUpdated = (handler: () => void) => {
  window.addEventListener(MATERIAL_UPDATED_EVENT, handler)
  return () => window.removeEventListener(MATERIAL_UPDATED_EVENT, handler)
}
