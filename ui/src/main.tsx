import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import App from "./App";
import { ProviderAvviso } from "./componenti/Avviso";
import "./index.css";

const client = new QueryClient({
  defaultOptions: {
    queries: {
      // il servizio e' sulla rete locale: se non risponde una volta, insistere
      // in automatico ha poco senso, meglio i tasti "Cerca di nuovo" a vista
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

// Il router "dei dati" (createBrowserRouter + RouterProvider) al posto di <BrowserRouter>:
// e' l'unico che offre useBlocker, con cui Etichette avvisa delle modifiche non salvate
// su OGNI uscita (menu, tasto indietro del browser, cambio di pagina) - 2 ottobre 2026.
// Un'unica rotta "*" che contiene tutta l'app: le rotte vere restano quelle di App.tsx.
const router = createBrowserRouter([
  {
    path: "*",
    element: (
      <ProviderAvviso>
        <App />
      </ProviderAvviso>
    ),
  },
]);

const contenitore = document.getElementById("root");
if (!contenitore) throw new Error("Manca #root in index.html");

createRoot(contenitore).render(
  <StrictMode>
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
