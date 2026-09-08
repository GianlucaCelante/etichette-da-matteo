import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
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

const contenitore = document.getElementById("root");
if (!contenitore) throw new Error("Manca #root in index.html");

createRoot(contenitore).render(
  <StrictMode>
    <QueryClientProvider client={client}>
      <BrowserRouter>
        <ProviderAvviso>
          <App />
        </ProviderAvviso>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
