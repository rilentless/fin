import { useEffect, useState } from "react";
import { AppError } from "../../../api/errorParser";
import Portal from "../../../shared/components/ui/Portal";
import { useAppMutation } from "../../../shared/hooks/useAppMutation";
import { authService } from "../../auth/authService";

interface Props {
  email: string;
  onClose: () => void;
  onVerified: () => void;
}

type Step = "ready" | "code";

const EmailVerifyPanel: React.FC<Props> = ({ email, onClose, onVerified }) => {
  const [step, setStep] = useState<Step>("ready");
  const [otp, setOtp] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [done, setDone] = useState(false);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const { mutate: sendOtp, isPending: isSending } = useAppMutation({
    mutationFn: () => authService.sendOtp({ email }),
    onSuccess: () => {
      setStep("code");
      setOtp("");
      setError("");
      setNotice("Code sent.");
    },
    onError: (err: AppError) => setError(err.message),
  });

  const { mutate: resendOtp, isPending: isResending } = useAppMutation({
    mutationFn: () => authService.resendOtp({ email }),
    onSuccess: () => {
      setOtp("");
      setError("");
      setNotice("A new code was sent.");
    },
    onError: (err: AppError) => setError(err.message),
  });

  const { mutate: verifyOtp, isPending: isVerifying } = useAppMutation({
    mutationFn: () => authService.verifyOtp({ email, otp }),
    onSuccess: () => {
      setError("");
      setNotice("");
      setDone(true);
    },
    onError: (err: AppError) => setError(err.message),
  });

  const handleVerify = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (otp.length !== 6) {
      setError("Enter the 6-digit code from your email.");
      return;
    }

    setError("");
    verifyOtp(undefined);
  };

  const isCodeRequestPending = isVerifying || isResending;

  return (
    <Portal>
      <>
      <button
        type="button"
        aria-label="Close email verification"
        className="fixed inset-0 z-30 cursor-default bg-black/30"
        onClick={onClose}
      />
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="verify-email-title"
        aria-busy={isSending || isCodeRequestPending}
        className="fixed left-1/2 top-1/2 z-40 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-lg border border-gray-200 bg-white p-5 shadow-xl sm:p-6"
      >
        <header className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h2
              id="verify-email-title"
              className="text-lg font-semibold text-gray-900"
            >
              {done
                ? "Email verified"
                : step === "ready"
                  ? "Verify your email"
                  : "Enter your code"}
            </h2>
            <p className="mt-1 break-all text-sm text-gray-500">{email}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-lg text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900"
          >
            ×
          </button>
        </header>

        <div className="mt-5">
          {done ? (
            <div className="space-y-4">
              <p role="status" className="text-sm text-gray-600">
                Your email has been verified.
              </p>
              <button
                type="button"
                onClick={onVerified}
                className="inline-flex min-h-11 w-full items-center justify-center rounded-lg bg-gray-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-black"
              >
                Done
              </button>
            </div>
          ) : step === "ready" ? (
            <div className="space-y-4">
              <p className="text-sm text-gray-600">
                We’ll send a 6-digit code to this address.
              </p>
              {error && (
                <p role="alert" className="text-sm text-red-600">
                  {error}
                </p>
              )}
              <button
                type="button"
                onClick={() => {
                  setError("");
                  sendOtp(undefined);
                }}
                disabled={isSending}
                className="inline-flex min-h-11 w-full items-center justify-center rounded-lg bg-gray-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-black disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isSending ? "Sending code…" : "Send code"}
              </button>
            </div>
          ) : (
            <form onSubmit={handleVerify} className="space-y-4">
              <p className="text-sm text-gray-600">
                Enter the 6-digit code sent to your email.
              </p>

              <div>
                <label
                  htmlFor="email-verification-code"
                  className="mb-1.5 block text-sm font-medium text-gray-700"
                >
                  Verification code
                </label>
                <input
                  id="email-verification-code"
                  type="text"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  placeholder="000000"
                  value={otp}
                  onChange={(event) => {
                    setOtp(event.target.value.replace(/\D/g, "").slice(0, 6));
                    setError("");
                  }}
                  autoFocus
                  required
                  disabled={isCodeRequestPending}
                  className="min-h-12 w-full rounded-lg border border-gray-200 px-3 text-center text-lg tracking-[0.3em] text-gray-900 outline-none transition focus:border-gray-400 focus:ring-2 focus:ring-gray-900/10 disabled:bg-gray-50"
                />
              </div>

              {error && (
                <p role="alert" className="text-sm text-red-600">
                  {error}
                </p>
              )}
              {notice && (
                <p role="status" aria-live="polite" className="text-sm text-gray-500">
                  {notice}
                </p>
              )}

              <button
                type="submit"
                disabled={isCodeRequestPending || otp.length !== 6}
                className="inline-flex min-h-11 w-full items-center justify-center rounded-lg bg-gray-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-black disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isVerifying ? "Verifying…" : "Verify email"}
              </button>

              <p className="text-center text-sm text-gray-500">
                Didn’t receive a code?{" "}
                <button
                  type="button"
                  onClick={() => {
                    setError("");
                    setNotice("");
                    resendOtp(undefined);
                  }}
                  disabled={isCodeRequestPending}
                  className="font-medium text-gray-900 underline-offset-2 hover:underline disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {isResending ? "Sending…" : "Resend"}
                </button>
              </p>
            </form>
          )}
        </div>
      </section>
      </>
    </Portal>
  );
};

export default EmailVerifyPanel;
