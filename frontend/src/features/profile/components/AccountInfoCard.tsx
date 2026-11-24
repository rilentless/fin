import type {UserProfileResponse} from "../profile.types";

interface Props {
  profile: UserProfileResponse;
  onOpenVerify: () => void;
}

const InfoRow: React.FC<{
  label: string;
  value: React.ReactNode;
  action?: React.ReactNode;
}> = ({label, value, action}) => (
  <div className="flex items-start justify-between gap-3 py-3.5 border-b border-gray-100 last:border-0">
    <span className="text-sm text-gray-500 shrink-0">{label}</span>
    <div className="flex min-w-0 items-center gap-2 sm:gap-3">
      <span className="min-w-0 text-right text-sm font-medium text-gray-900">
        {value}
      </span>
      {action}
    </div>
  </div>
);

const AccountInfoCard: React.FC<Props> = ({profile, onOpenVerify}) => {
  const isVerified = profile.status === "ACTIVE";

  return (
    <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
      <div className="px-4 sm:px-5 pt-4 pb-2 border-b border-gray-100">
        <p className="text-xs font-semibold uppercase tracking-widest text-gray-400">
          Account details
        </p>
      </div>

      <div className="px-4 sm:px-5">
        <InfoRow
          label="Email"
          value={<span className="block max-w-full break-all">{profile.email}</span>}
        />

        <InfoRow
          label="Account status"
          value={
            <span className={isVerified ? "text-green-600" : "text-red-600"}>
              {profile.status
                .replaceAll("_", " ")
                .toLowerCase()
                .replace(/\b\w/g, (char) => char.toUpperCase())}
            </span>
          }
        />

        <InfoRow
          label="Email verified"
          value={
            <span className={isVerified ? "text-blue-600" : "text-red-600"}>
              {isVerified ? "Verified" : "Not verified"}
            </span>
          }
          action={
            isVerified ? (
              <span
                aria-label="Email verified"
                className="inline-flex h-4 w-4 items-center justify-center rounded-full bg-blue-50 text-blue-600"
              >
                <svg
                  width="9"
                  height="9"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="3"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </span>
            ) : (
              <button
                type="button"
                onClick={onOpenVerify}
                className="whitespace-nowrap rounded-lg border border-gray-900 px-2.5 py-1 text-xs font-semibold text-gray-900 transition-colors hover:bg-gray-900 hover:text-white"
              >
                Verify now
              </button>
            )
          }
        />
      </div>
    </div>
  );
};

export default AccountInfoCard;