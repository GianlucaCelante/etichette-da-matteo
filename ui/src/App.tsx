import { Navigate, Route, Routes } from "react-router-dom";
import Guscio from "./componenti/Guscio";
import Stampa from "./viste/Stampa";
import Etichette from "./viste/Etichette";
import Storico from "./viste/Storico";
import Impostazioni from "./viste/Impostazioni";

// Quattro viste dietro un solo guscio, come nel prototipo: Stampa e' la
// rotta di partenza.
export default function App() {
  return (
    <Routes>
      <Route element={<Guscio />}>
        <Route index element={<Navigate to="/stampa" replace />} />
        <Route path="/stampa" element={<Stampa />} />
        <Route path="/etichette" element={<Etichette />} />
        <Route path="/storico" element={<Storico />} />
        <Route path="/impostazioni" element={<Impostazioni />} />
        <Route path="*" element={<Navigate to="/stampa" replace />} />
      </Route>
    </Routes>
  );
}
