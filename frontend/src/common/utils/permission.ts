import { isArray } from "@@/utils/validate"
import { useUserStore } from "@/pinia/stores/user"

export function checkPermission(permissionRoles: string[]): boolean {
  if (isArray(permissionRoles) && permissionRoles.length > 0) {
    const { roles } = useUserStore()
    return roles.some(role => permissionRoles.includes(role))
  } else {
    console.error("参数必须是非空数组，例如：checkPermission(['admin', 'user'])")
    return false
  }
}
