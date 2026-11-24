import {Link} from "react-router-dom";
import {useAuth} from "../../../shared/hooks/useAuth";

const HeroSection: React.FC = () => {
  const {isAuthenticated} = useAuth();

  return (
    <section className="relative overflow-hidden bg-[#f8faff] px-5 py-20 sm:py-24">
      {/* Background decoration */}
      <div className="pointer-events-none absolute -right-32 -top-32 h-96 w-96 rounded-full bg-blue-100/60 blur-3xl"/>
      <div
        className="pointer-events-none absolute -bottom-32 -left-32 h-80 w-80 rounded-full bg-indigo-100/40 blur-3xl"/>

      <div className="relative mx-auto grid max-w-6xl items-center gap-16 lg:grid-cols-2">

        {/* Left: Hero content */}
        <div className="max-w-xl">
          <h1 className="text-4xl font-semibold leading-[1.15] tracking-tight text-gray-900 sm:text-5xl lg:text-6xl">
            Take control of your money.
            <span className="mt-2 block text-blue-600">
              Build a better future.
            </span>
          </h1>

          <p className="mt-6 max-w-lg text-base leading-7 text-gray-600 sm:text-lg">
            Track your spending, plan your budgets, and understand your finances
            without the complexity. Everything you need, in one place.
          </p>

          {/* Actions */}
          <div className="mt-8 flex flex-col gap-3 sm:flex-row">
            <Link
              to={isAuthenticated ? "/dashboard" : "/signup"}
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-6 py-3.5 text-sm font-semibold text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700 hover:shadow-blue-600/30"
            >
              {isAuthenticated ? "Go to dashboard" : "Get started"}
              <span aria-hidden="true">→</span>
            </Link>

            {!isAuthenticated && (
              <Link
                to="/login"
                className="inline-flex items-center justify-center rounded-lg border border-gray-200 bg-white px-6 py-3.5 text-sm font-semibold text-gray-700 transition hover:border-gray-300 hover:bg-gray-50"
              >
                Sign in
              </Link>
            )}
          </div>

          {/* Small supporting text */}
          <div className="mt-6 flex items-center gap-2 text-sm text-gray-500">
            <span className="text-green-600">✓</span>
            Simple tracking. Smarter decisions. Better habits.
          </div>
        </div>

        {/* Right: Dashboard preview */}
        <div className="relative mx-auto w-full max-w-lg">
          <div className="absolute -inset-4 rounded-lg bg-blue-200/40 blur-2xl"/>
          <div
            className="relative rounded-lg border border-gray-200/80 bg-white p-5 shadow-xl shadow-blue-900/5 sm:p-7">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-500">Overview</p>
                <h2 className="mt-1 text-xl font-semibold text-gray-900">
                  Your finances
                </h2>
              </div>

              <div className="rounded-lg bg-blue-50 px-3 py-1.5 text-xs font-medium text-blue-600">
                This month
              </div>
            </div>
            <div className="mt-7 rounded-lg bg-gradient-to-br from-blue-600 to-blue-700 p-5 text-white">
              <p className="text-sm text-blue-100">Total balance</p>
              <h3 className="mt-2 text-xl font-semibold tracking-tight">
                ₹48,250.00
              </h3>
              <div className="mt-4 flex items-center gap-2 text-sm">
                <span className="rounded-lg bg-white/15 px-2 py-1 text-blue-50">
                  ↑ 12.8%
                </span>
                <span className="text-blue-100">from last month</span>
              </div>
            </div>
            <div className="mt-5 grid grid-cols-2 gap-4">
              <div className="rounded-lg border border-gray-100 bg-gray-50 p-4">
                <div className="flex items-center gap-2 text-sm text-gray-500">
                  <span className="h-2.5 w-2.5 rounded-full bg-green-500"/>
                  Income
                </div>
                <p className="mt-2 text-xl font-semibold text-gray-900">
                  ₹32,000
                </p>
              </div>

              <div className="rounded-lg border border-gray-100 bg-gray-50 p-4">
                <div className="flex items-center gap-2 text-sm text-gray-500">
                  <span className="h-2.5 w-2.5 rounded-full bg-orange-500"/>
                  Expenses
                </div>
                <p className="mt-2 text-xl font-semibold text-gray-900">
                  ₹12,450
                </p>
              </div>
            </div>

            {/* Spending progress */}
            <div className="mt-6">
              <div className="flex items-center justify-between text-sm">
                <span className="font-medium text-gray-700">
                  Monthly budget
                </span>
                <span className="text-gray-500">68% used</span>
              </div>
              <div className="mt-3 h-2.5 overflow-hidden rounded-full bg-gray-100">
                <div className="h-full w-[68%] rounded-full bg-blue-500"/>
              </div>
              <p className="mt-2 text-xs text-gray-500">
                ₹12,450 of ₹18,000 spent
              </p>
            </div>
            <div className="mt-5 flex items-center gap-3 rounded-lg p-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-blue-100 text-blue-600">
                ✦
              </div>
              <div>
                <p className="text-sm font-medium text-gray-800">
                  You’re on track!
                </p>
                <p className="text-xs text-gray-500">
                  Your spending is within your budget.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default HeroSection;
