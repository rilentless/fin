import {FaGithub, FaLinkedin} from "react-icons/fa";
import {about} from "../about";

const SOCIAL_ICONS = {
  GitHub: FaGithub,
  LinkedIn: FaLinkedin,
} as const;

export default function AboutPage() {
  return (
    <div className="mx-auto w-full min-w-0 max-w-xl py-8">
      <section className="rounded-lg border border-gray-200 bg-white px-8 py-10 sm:px-10">
        <header className="text-center">
          <div className="mx-auto flex size-16 items-center justify-center">
            <img
              src="/favicon.png"
              alt="logo"
              className="size-16 rounded-full object-cover"
            />
          </div>
          <p className="mt-1 text-sm text-gray-500">Version {about.version}</p>
        </header>

        <dl className="mt-7 pt-4">
          <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-2">
            <dt className="text-sm text-gray-500">License</dt>
            <dd className="text-sm font-medium text-gray-900">
              {about.license}
            </dd>
          </div>
        </dl>

        <div className="mt-4 border-t border-gray-100 pt-5">
          <h2 className="text-xs font-medium normal-case tracking-wide text-gray-500">
            Social Links
          </h2>
          <ul className="mt-3 flex flex-wrap gap-3">
            {about.socialLinks.map(({label, url}) => {
              const Icon = SOCIAL_ICONS[label];
              return (
                <li key={label}>
                  <a
                    href={url}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
                  >
                    <Icon aria-hidden="true"/>
                    {label}
                  </a>
                </li>
              );
            })}
          </ul>
        </div>
      </section>
    </div>
  );
}
