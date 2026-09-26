export type PairingRole = "admin" | "client";

/** The desktop is authoritative; this snapshot never derives rights from cached mobile data. */
export function pairingAccess(role: PairingRole, cloudDesktopAccess: boolean) {
  return {
    role,
    scopes: role === "admin" ? ["admin", "client"] : ["client"],
    permissions: {
      chat: true,
      approvals: true,
      routines: true,
      manageBots: role === "admin",
      manageSettings: role === "admin",
      cloudDesktop: cloudDesktopAccess,
    },
  };
}
