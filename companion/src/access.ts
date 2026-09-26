import { effectivePermissions, PERMISSIONS, type AccessMode, type PermissionMap } from "./permissions.ts";
export type PairingRole = AccessMode;

/** The desktop is authoritative; this snapshot never derives rights from cached mobile data. */
export function pairingAccess(role: PairingRole, cloudDesktopAccess: boolean, custom?: PermissionMap) {
  const grants = effectivePermissions(role, custom, cloudDesktopAccess);
  return {
    role,
    scopes: role !== "client" ? ["admin", "client"] : ["client"],
    capabilities: PERMISSIONS.map(([id, labelDe, labelEn, descriptionDe, descriptionEn]) => ({ id, labelDe, labelEn, descriptionDe, descriptionEn, allowed: grants[id] })),
    permissions: {
      chat: grants.chatRead && grants.chatSend,
      approvals: grants.approvals,
      routines: grants.routines,
      manageBots: grants.botEdit,
      manageSettings: grants.workspace,
      cloudDesktop: grants.cloudDesktop,
    },
  };
}
