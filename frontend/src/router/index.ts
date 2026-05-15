import type { RouteRecordRaw } from "vue-router"
import { createRouter } from "vue-router"
import { routerConfig } from "@/router/config"
import { registerNavigationGuard } from "@/router/guard"
import { flatMultiLevelRoutes } from "./helper"

const Layouts = () => import("@/layouts/index.vue")

export const constantRoutes: RouteRecordRaw[] = [
  {
    path: "/redirect",
    component: Layouts,
    meta: {
      hidden: true
    },
    children: [
      {
        path: ":path(.*)",
        component: () => import("@/pages/redirect/index.vue")
      }
    ]
  },
  {
    path: "/403",
    component: () => import("@/pages/error/403.vue"),
    meta: {
      hidden: true
    }
  },
  {
    path: "/404",
    component: () => import("@/pages/error/404.vue"),
    meta: {
      hidden: true
    },
    alias: "/:pathMatch(.*)*"
  },
  {
    path: "/login",
    component: () => import("@/pages/login/index.vue"),
    meta: {
      hidden: true
    }
  },
  {
    path: "/",
    redirect: "/overview",
    meta: {
      hidden: true
    }
  },
  {
    path: "/overview",
    component: Layouts,
    redirect: "/overview/index",
    children: [
      {
        path: "index",
        name: "Dashboard",
        component: () => import("@/pages/dashboard/index.vue"),
        meta: {
          title: "概览",
          elIcon: "DataBoard",
          affix: true
        }
      }
    ]
  },
  {
    path: "/assistant",
    component: Layouts,
    redirect: "/assistant/index",
    children: [
      {
        path: "index",
        name: "Assistant",
        component: () => import("@/pages/assistant/index.vue"),
        meta: {
          title: "AI 助手",
          elIcon: "ChatDotRound"
        }
      }
    ]
  },
  {
    path: "/rooms",
    component: Layouts,
    redirect: "/rooms/index",
    children: [
      {
        path: "index",
        name: "Rooms",
        component: () => import("@/pages/rooms/index.vue"),
        meta: {
          title: "会议空间",
          elIcon: "OfficeBuilding"
        }
      }
    ]
  },
  {
    path: "/calendar",
    component: Layouts,
    redirect: "/calendar/index",
    children: [
      {
        path: "index",
        name: "Calendar",
          component: () => import("@/pages/calendar/index.vue"),
        meta: {
          title: "预约日历",
          elIcon: "Calendar"
        }
      }
    ]
  },
  {
    path: "/reservations",
    component: Layouts,
    redirect: "/reservations/index",
    children: [
      {
        path: "index",
        name: "MyReservations",
        component: () => import("@/pages/reservations/index.vue"),
        meta: {
          title: "我的预约",
          elIcon: "Tickets"
        }
      }
    ]
  }
]

export const dynamicRoutes: RouteRecordRaw[] = [
  {
    path: "/admin",
    component: Layouts,
    redirect: "/admin/rooms",
    name: "Admin",
    meta: {
      title: "管理端",
      elIcon: "Management",
      roles: ["admin"],
      alwaysShow: true
    },
    children: [
      {
        path: "reservations",
        name: "AdminReservations",
        component: () => import("@/pages/admin/reservations/index.vue"),
        meta: {
          title: "预约审核",
          roles: ["admin"]
        }
      },
      {
        path: "rooms",
        name: "AdminRooms",
        component: () => import("@/pages/admin/rooms/index.vue"),
        meta: {
          title: "会议室管理",
          roles: ["admin"]
        }
      },
      {
        path: "devices",
        name: "AdminDevices",
        component: () => import("@/pages/admin/devices/index.vue"),
        meta: {
          title: "设备管理",
          roles: ["admin"]
        }
      },
      {
        path: "device-stats",
        name: "AdminDeviceStats",
        component: () => import("@/pages/admin/device-stats/index.vue"),
        meta: {
          title: "设备绑定统计",
          roles: ["admin"]
        }
      },
      {
        path: "stats",
        name: "AdminStats",
        component: () => import("@/pages/admin/stats/index.vue"),
        meta: {
          title: "统计分析",
          roles: ["admin"]
        }
      }
    ]
  }
]

export const router = createRouter({
  history: routerConfig.history,
  routes: routerConfig.thirdLevelRouteCache ? flatMultiLevelRoutes(constantRoutes) : constantRoutes
})

export function resetRouter() {
  try {
    router.getRoutes().forEach((route) => {
      const { name, meta } = route
      if (name && meta.roles?.length) {
        router.hasRoute(name) && router.removeRoute(name)
      }
    })
  } catch {
    location.reload()
  }
}

registerNavigationGuard(router)
