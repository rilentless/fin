import packageMetadata from "../../../package.json";

export type SocialLinkLabel = "GitHub" | "LinkedIn";

export interface SocialLink {
  label: SocialLinkLabel;
  url: string;
}

export const about = {
  appName: "Fin",
  version: packageMetadata.version,
  license: "Apache License 2.0",
  socialLinks: [
    {label: "GitHub", url: "https://github.com/ripanbaidya"},
    {label: "LinkedIn", url: "https://www.linkedin.com/in/ripanbaidya/"},
  ] satisfies SocialLink[],
};
