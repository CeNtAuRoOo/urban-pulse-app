import { Body, Heading, NavLink } from "../../components/ui";
export default function NotFound() {
  return (
    <>
      <Heading level={1}>Página no encontrada</Heading>
      <Body>La dirección no corresponde a una sección de UrbanPulse.</Body>
      <NavLink href="/">Volver al inicio</NavLink>
    </>
  );
}
