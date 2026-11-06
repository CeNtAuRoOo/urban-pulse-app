import { NavLink, Panel } from "../../components/ui";
import { useSession } from "../../services/session";
export default function Home() {
  const { session } = useSession();
  return (
    <>
      <div className="page-heading">
        <p className="eyebrow">URBANPULSE · DEMO</p>
        <h1>Hola, {session?.user.displayName}</h1>
        <p>Registra avisos y consulta su información desde un único lugar.</p>
      </div>
      <div className="cards">
        <Panel title="Registrar una incidencia">
          <p>Describe el problema, indica sus coordenadas y selecciona una categoría.</p>
          <NavLink href="/incidents/new" className="button">
            Crear incidencia
          </NavLink>
        </Panel>
        <Panel title="Consultar tus reportes">
          <p>Accede al detalle, corrige un reporte inicial y consulta su historial público.</p>
          <NavLink href="/incidents" className="button secondary">
            Ver mis incidencias
          </NavLink>
        </Panel>
        <Panel title="Catálogos municipales">
          <p>Consulta las categorías y los departamentos disponibles.</p>
          <NavLink href="/categories">Ver categorías →</NavLink>
          <br />
          <NavLink href="/departments">Ver departamentos →</NavLink>
        </Panel>
      </div>
    </>
  );
}
