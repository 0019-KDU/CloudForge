import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { roleLabel } from "@/lib/utils/labels";
import { ROLES, type Role } from "@/types/api";

const DESCRIPTIONS: Record<Role, string> = {
  ENGINEER: "Requests resources.",
  MANAGER: "Approves reports' requests; can also request resources.",
  PLATFORM_ADMIN: "Manages users, catalog and provisioning. Cannot request resources.",
  AUDITOR: "Read-only access to all requests and the audit log.",
};

interface RoleCheckboxesProps {
  value: Role[];
  onChange: (roles: Role[]) => void;
  disabled?: boolean;
  idPrefix: string;
}

export function RoleCheckboxes({ value, onChange, disabled, idPrefix }: RoleCheckboxesProps) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {ROLES.map((role) => {
        const id = `${idPrefix}-${role}`;
        return (
          <div key={role} className="flex items-start gap-2.5 rounded-md border p-3">
            <Checkbox
              id={id}
              checked={value.includes(role)}
              disabled={disabled}
              onCheckedChange={(checked) =>
                onChange(checked === true ? [...value, role] : value.filter((existing) => existing !== role))
              }
              aria-describedby={`${id}-desc`}
              className="mt-0.5"
            />
            <div>
              <Label htmlFor={id}>{roleLabel(role)}</Label>
              <p id={`${id}-desc`} className="mt-0.5 text-xs text-muted-foreground">
                {DESCRIPTIONS[role]}
              </p>
            </div>
          </div>
        );
      })}
    </div>
  );
}
