import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/modules/user'

type PermissionElement = HTMLElement & {
  __permissionOriginalDisplay?: string
}

const applyPermission = (el: PermissionElement, binding: DirectiveBinding<string>) => {
  const userStore = useUserStore()
  const requiredPermission = binding.value

  if (el.__permissionOriginalDisplay === undefined) {
    el.__permissionOriginalDisplay = el.style.display
  }

  if (!requiredPermission) {
    el.style.display = el.__permissionOriginalDisplay
    return
  }

  const hasPermission = userStore.hasPermission(requiredPermission)
  el.style.display = hasPermission ? el.__permissionOriginalDisplay : 'none'
}

export const vPermission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string>) {
    applyPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string>) {
    applyPermission(el, binding)
  }
}
