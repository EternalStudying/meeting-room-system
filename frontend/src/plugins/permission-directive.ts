import type { App, Directive } from "vue"
import { isArray } from "@@/utils/validate"
import { useUserStore } from "@/pinia/stores/user"

const permission: Directive = {
  mounted(el, binding) {
    const { value: permissionRoles } = binding
    const { roles } = useUserStore()
    if (isArray(permissionRoles) && permissionRoles.length > 0) {
      const hasPermission = roles.some(role => permissionRoles.includes(role))
      hasPermission || el.parentNode?.removeChild(el)
    } else {
      throw new Error(`参数必须是非空数组，例如：v-permission="['admin', 'user']"`)
    }
  }
}

export function installPermissionDirective(app: App) {
  app.directive("permission", permission)
}
