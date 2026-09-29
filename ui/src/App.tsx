import { Navigate, Route, Routes } from "react-router-dom";
import Guscio from "./componenti/Guscio";
import Stampa from "./viste/Stampa";
import Etichette from "./viste/Etichette";
import Ingredienti from "./viste/Ingredienti";
import MerceArrivata from "./viste/MerceArrivata";
import Storico from "./viste/Storico";
import Impostazioni from "./viste/Impostazioni";

// Le viste dietro un solo guscio, come nel prototipo: Stampa e' la rotta di
// partenza. "Merce arrivata" e' una rotta a parte (non una finestra sopra
// Ingredienti, /ingredienti/arrivo): cosi' l'indirizzo la ricorda se si
// ricarica la pagina, come le altre viste.
export default function App() {
  return (
    <Routes>
      <Route element={<Guscio />}>
        <Route index element={<Navigate to="/stampa" replace />} />
        <Route path="/stampa" element={<Stampa />} />
        <Route path="/etichette" element={<Etichette />} />
        <Route path="/ingredienti" element={<Ingredienti />} />
        <Route path="/ingredienti/arrivo" element={<MerceArrivata />} />
        <Route path="/storico" element={<Storico />} />
        <Route path="/impostazioni" element={<Impostazioni />} />
        <Route path="*" element={<Navigate to="/stampa" replace />} />
      </Route>
    </Routes>
  );
}
