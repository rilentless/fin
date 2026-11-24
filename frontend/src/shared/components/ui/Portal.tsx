import type { PropsWithChildren } from "react";
import { createPortal } from "react-dom";

const Portal: React.FC<PropsWithChildren> = ({ children }) =>
  createPortal(children, document.body);

export default Portal;
