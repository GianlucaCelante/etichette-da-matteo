"""Prova di stampa dell'etichetta "Completa" a 300 dpi, per misurare i corpi con il righello.

Serve a rispondere a tre domande che il canvas non puo' risolvere:
  1. la zona a due colonne esce come disegnata (filetto, allineamenti, colonna a un terzo);
  2. quanto vengono grandi davvero i corpi dichiarati dal canvas (18 pt il titolo, 6 pt gli
     ingredienti, 28 pt la quantita');
  3. se gli ingredienti stanno sopra il minimo di legge di 1,2 mm di altezza della x.

Il canvas disegna l'etichetta larga 58,9 mm e alta ~32 mm; la mappatura della stampante dice
invece che sul rotolo da 62 mm i 58,9 mm sono il lato che attraversa la testina e la lunghezza
lungo il nastro e' libera. Le due letture danno etichette molto diverse, quindi si stampano
entrambe e si misurano:

  --variante a   larghezza 58,9 mm  (lettura del canvas), altezza libera
  --variante b   larghezza 107,7 mm (stessa proporzione del canvas con 58,9 mm come altezza),
                 ruotata di 90 gradi come farebbe il renderer sul rotolo da 62
  --scaletta     righello dei corpi: la stessa riga di ingredienti da 5 a 18 pt, con il
                 riferimento di 1,2 mm di altezza della x

Uso:
  python ql_prova_etichetta.py --render-only          # solo i PNG di anteprima
  python ql_prova_etichetta.py                        # stampa tutte e tre
  python ql_prova_etichetta.py --variante a           # stampa solo la prima
"""
import argparse
import os
import sys
import time

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(__file__))
from ql_probe import UsbPrinter, decode_status  # noqa: E402
from ql_settings_readout import poll_read, drain  # noqa: E402
from ql_testprint import CONTINUOUS, DOTS_PER_MM, build_job, fmt_status  # noqa: E402

PT = DOTS_PER_MM * 25.4 / 72.0  # punti tipografici -> dot a 300 dpi (4,1667)
FONT_BOLD = r"C:\Windows\Fonts\arialbd.ttf"
FONT_REG = r"C:\Windows\Fonts\arial.ttf"

_cache = {}


def font(pt, bold=False):
    key = (round(pt * PT), bold)
    if key not in _cache:
        _cache[key] = ImageFont.truetype(FONT_BOLD if bold else FONT_REG, key[0])
    return _cache[key]


def mm(v):
    return int(round(v * DOTS_PER_MM))


# --- testo a spezzoni (grassetto dentro il paragrafo) ---------------------------------------

def wrap(d, runs, pt, max_w):
    """runs = [(testo, grassetto)]. Torna righe, ognuna [(testo, font)].

    Gli spazi sono gettoni a se': cosi' uno spazio a cavallo fra due spezzoni (". di " dopo un
    grassetto) non si perde, che era il difetto della prima versione.
    """
    gettoni = []
    for testo, bold in runs:
        f = font(pt, bold)
        buf = ""
        for ch in testo:
            if ch == " ":
                if buf:
                    gettoni.append((buf, f, False))
                    buf = ""
                gettoni.append((" ", f, True))
            else:
                buf += ch
        if buf:
            gettoni.append((buf, f, False))

    righe, riga, w = [], [], 0
    for testo, f, spazio in gettoni:
        tw = d.textlength(testo, font=f)
        if riga and not spazio and w + tw > max_w:
            while riga and riga[-1][0] == " ":
                w -= d.textlength(" ", font=riga[-1][1])
                riga.pop()
            righe.append(riga)
            riga, w = [], 0
        if not riga and spazio:
            continue
        riga.append((testo, f))
        w += tw
    if riga:
        righe.append(riga)
    return righe


def taglia(d, testo, f, max_w):
    """Accorcia con i puntini finche' non ci sta: cosi' un traboccamento si vede per quello che e'."""
    if d.textlength(testo, font=f) <= max_w:
        return testo
    while testo and d.textlength(testo + "…", font=f) > max_w:
        testo = testo[:-1]
    return testo + "…"


def disegna_righe(d, righe, x, y, interlinea):
    for riga in righe:
        cx = x
        for t, f in riga:
            d.text((cx, y), t, font=f, fill=0)
            cx += d.textlength(t, font=f)
        y += interlinea
    return y


def alt(pt, fattore=1.18):
    return int(round(pt * PT * fattore))


# --- contenuto dell'etichetta "Completa" ----------------------------------------------------

INGREDIENTI = [
    ("INGREDIENTI: ", True),
    ("Acqua, Mix farine [Amido resistente di tapioca, Proteina vitale di ", False),
    ("FRUMENTO", True), (", Fibra di ", False), ("FRUMENTO", True),
    (", Lievito madre di farina di ", False), ("FRUMENTO", True),
    (" in polvere, Lievito disattivato, Proteina di ", False), ("AVENA", True),
    ("], Olio di girasole, Sale iodato, Lievito di birra compresso, Coadiuvante in polvere "
     "per panificazione [Farina di ", False), ("GRANO", True),
    (" tenero tipo 0, Enzimi], Miscela per spolvero [", False), ("SEMOLA", True),
    (" rimacinata di ", False), ("GRANO", True), (" duro, Farina di riso, Farina di mais].", False),
]
PUO_CONTENERE = [
    ("Può contenere: ", False), ("Latte", True), (", Lupini, ", False), ("Senape", True),
    (", ", False), ("Sesamo", True), (", ", False), ("Soia", True), (", ", False), ("Uova", True),
]
NUTRIZIONALI = [
    ("ENERGIA", "385 kJ / 91 kcal"), ("GRASSI", "2,6 g"), ("di cui saturi", "0,5 g"),
    ("CARBOIDRATI", "2 g"), ("di cui zuccheri", "0,7 g"), ("PROTEINE", "15 g"), ("SALE", "1,5 g"),
]
PRODUTTORE = ("Michi s.n.c. di Michele Alberto Crivellari - "
              "Via Brigata Marche 257 - 31030 Carbonera (TV)")

# corpi dichiarati dalle tendine del canvas
CORPI = {"titolo": 18, "ingredienti": 6, "puo_contenere": 6, "scadenza": 8, "lotto": 6,
         "quantita": 28, "quantita_etichetta": 8, "nutrizionali": 6, "produttore": 6}


def render_etichetta(larghezza_mm, quota_destra=1 / 3.0, margine_mm=2.0):
    """Compone l'etichetta come la legge l'utente. L'altezza esce dal contenuto."""
    W = mm(larghezza_mm)
    M = mm(margine_mm)
    interno = W - 2 * M
    misura = Image.new("L", (W, mm(400)), 255)
    d = ImageDraw.Draw(misura)

    y = M
    # titolo, con il filetto sotto: va a capo se non ci sta, non esce dal bordo
    righe = wrap(d, [("BASE PIZZA LOW CARB ARTIGIANALE", True)], CORPI["titolo"], interno)
    y = disegna_righe(d, righe, M, y, alt(CORPI["titolo"], 1.1))
    d.line([(M, y), (W - M, y)], fill=0, width=2)
    y += mm(0.8)

    righe = wrap(d, INGREDIENTI, CORPI["ingredienti"], interno)
    y = disegna_righe(d, righe, M, y, alt(CORPI["ingredienti"], 1.25))
    y += mm(0.6)
    righe = wrap(d, PUO_CONTENERE, CORPI["puo_contenere"], interno)
    y = disegna_righe(d, righe, M, y, alt(CORPI["puo_contenere"], 1.25))
    y += mm(1.2)

    # --- zona a due colonne -----------------------------------------------------------------
    gutter = mm(2.0)
    w_dx = int(round((interno - gutter) * quota_destra))
    w_sx = interno - gutter - w_dx
    x_dx = M + w_sx + gutter
    top = y

    ys = y
    righe = wrap(d, [("da consumare entro ", False), ("10/09/2026", True)], CORPI["scadenza"], w_sx)
    ys = disegna_righe(d, righe, M, ys, alt(CORPI["scadenza"], 1.25))
    righe = wrap(d, [("CONSERVAZIONE FUORI DAL FRIGO", False)], CORPI["scadenza"], w_sx)
    ys = disegna_righe(d, righe, M, ys, alt(CORPI["scadenza"], 1.25))
    righe = wrap(d, [("L 20260903-005", False)], CORPI["lotto"], w_sx)
    ys = disegna_righe(d, righe, M, ys, alt(CORPI["lotto"], 1.25))
    ys += mm(0.8)
    d.text((M, ys), "Quantità", font=font(CORPI["quantita_etichetta"], True), fill=0)
    ys += alt(CORPI["quantita_etichetta"], 1.2)
    d.text((M, ys), "2148 g", font=font(CORPI["quantita"], True), fill=0)
    ys += alt(CORPI["quantita"], 1.05)
    ys += mm(1.0)
    righe = wrap(d, [(PRODUTTORE, False)], CORPI["produttore"], w_sx)
    ys = disegna_righe(d, righe, M, ys, alt(CORPI["produttore"], 1.25))

    yd = top
    fn = font(CORPI["nutrizionali"], True)
    t = "(100 g)"
    w_t = d.textlength(t, font=fn)
    d.text((x_dx, yd), taglia(d, "VALORI NUTRIZIONALI", fn, w_dx - w_t - mm(1)), font=fn, fill=0)
    d.text((x_dx + w_dx - w_t, yd), t, font=fn, fill=0)
    yd += alt(CORPI["nutrizionali"], 1.2)
    d.line([(x_dx, yd), (x_dx + w_dx, yd)], fill=0, width=2)
    yd += mm(0.3)
    for voce, val in NUTRIZIONALI:
        grassetto = not voce.startswith("di cui")
        f = font(CORPI["nutrizionali"], grassetto)
        fv = font(CORPI["nutrizionali"], False)
        w_v = d.textlength(val, font=fv)
        d.text((x_dx, yd), taglia(d, voce, f, w_dx - w_v - mm(1)), font=f, fill=0)
        d.text((x_dx + w_dx - w_v, yd), val, font=fv, fill=0)
        yd += alt(CORPI["nutrizionali"], 1.3)

    fondo = max(ys, yd) + M
    # filetto verticale fra le colonne, per tutta la zona
    d.line([(x_dx - gutter // 2, top), (x_dx - gutter // 2, fondo - M)], fill=0, width=2)
    return misura.crop((0, 0, W, fondo))


def render_scaletta(larghezza_mm=98.0):
    W = mm(larghezza_mm)
    M = mm(3.0)
    im = Image.new("L", (W, mm(200)), 255)
    d = ImageDraw.Draw(im)
    y = M
    d.text((M, y), "SCALETTA DEI CORPI - stessa riga di ingredienti", font=font(9, True), fill=0)
    y += alt(9, 1.6)
    # riferimento di legge: barra alta 1,2 mm (altezza della x minima)
    d.rectangle([M, y, M + mm(6), y + mm(1.2)], fill=0)
    d.text((M + mm(7), y - mm(0.3)), "1,2 mm = altezza della x minima di legge",
           font=font(7), fill=0)
    y += mm(1.2) + alt(7, 1.6)
    minimo = mm(1.2)
    tracciato = False
    for pt in [5, 6, 7, 8, 9, 10, 11, 12, 14, 16, 18]:
        f = font(pt)
        bbox = d.textbbox((0, 0), "x", font=f)
        hx = bbox[3] - bbox[1]
        if hx >= minimo and not tracciato:
            # la riga dove si passa il minimo di legge: sopra sono fuori norma
            y += mm(1.0)
            d.line([(M, y), (W - M, y)], fill=0, width=2)
            d.text((M, y + mm(0.5)), "sopra questa riga: altezza della x sotto 1,2 mm",
                   font=font(6, True), fill=0)
            y += mm(0.5) + alt(6, 1.8)
            tracciato = True
        d.text((M, y), "%2d pt" % pt, font=font(7, True), fill=0)
        # tacca dell'altezza della x di questo corpo, subito accanto al numero
        xb = M + mm(9)
        d.rectangle([xb, y, xb + mm(1.5), y + hx], fill=0)
        d.text((xb + mm(2.2), y), "%.1f mm" % (hx / DOTS_PER_MM), font=font(6), fill=0)
        xt = M + mm(22)
        d.text((xt, y), taglia(d, "Acqua, Mix farine, Proteina di FRUMENTO", f, W - M - xt),
               font=f, fill=0)
        y += alt(pt, 1.5)
    y += mm(2)
    # righello in mm lungo il fondo
    d.line([(M, y), (M + mm(50), y)], fill=0, width=2)
    for i in range(51):
        x = M + mm(i)
        h = mm(2.5) if i % 10 == 0 else (mm(1.6) if i % 5 == 0 else mm(0.9))
        d.line([(x, y), (x, y + h)], fill=0, width=2 if i % 10 == 0 else 1)
        if i % 10 == 0:
            d.text((x + mm(0.4), y + mm(2.8)), str(i), font=font(6), fill=0)
    y += mm(7)
    return im.crop((0, 0, W, y))


def con_scaffale(etichetta, didascalia, larghezza_foglio=None):
    """Mette l'etichetta su un foglio con cornice e righello in mm, per misurarla."""
    M = mm(3.0)
    W = larghezza_foglio or (etichetta.size[0] + 2 * M)
    h = etichetta.size[1] + mm(14)
    im = Image.new("L", (W, h), 255)
    d = ImageDraw.Draw(im)
    d.text((M, mm(1)), didascalia, font=font(7), fill=0)
    x0, y0 = M, mm(6)
    im.paste(etichetta, (x0, y0))
    d.rectangle([x0 - 1, y0 - 1, x0 + etichetta.size[0], y0 + etichetta.size[1]], outline=0, width=1)
    # righello in mm sotto il bordo inferiore
    yr = y0 + etichetta.size[1] + mm(2)
    n = int(etichetta.size[0] / DOTS_PER_MM)
    d.line([(x0, yr), (x0 + mm(n), yr)], fill=0, width=1)
    for i in range(n + 1):
        x = x0 + mm(i)
        hh = mm(2.0) if i % 10 == 0 else (mm(1.2) if i % 5 == 0 else mm(0.6))
        d.line([(x, yr), (x, yr + hh)], fill=0, width=1)
        if i % 10 == 0:
            d.text((x + mm(0.3), yr + mm(2.2)), str(i), font=font(6), fill=0)
    return im.crop((0, 0, W, yr + mm(5)))


def stampa(p, im, media_mm, autocut=True):
    job, righe, vuote = build_job(im, media_mm, autocut=autocut)
    print("  %d linee raster (%d vuote), job %.1f KB" % (righe, vuote, len(job) / 1024), flush=True)
    p.write(job)
    # la fine e' lo stato 0x01 "stampa completata" (il 0x06 e' solo il cambio di fase: se si
    # ripartisse da li' si manderebbe il job seguente mentre la stampante sta ancora stampando)
    fine = time.time() + 90
    while time.time() < fine:
        d = poll_read(p, max_ms=3000)
        # una lettura puo' contenere piu' stati accodati: si guardano tutti, in ordine
        for i in range(0, len(d) - 31, 32):
            blocco = d[i:i + 32]
            s = decode_status(blocco)
            print("  stato:", fmt_status(blocco), flush=True)
            if s["error1"] or s["error2"]:
                return False
            if s["status_type"].startswith("0x01"):
                return True
    print("  nessuna conferma di fine stampa entro 90 s")
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--render-only", action="store_true")
    ap.add_argument("--variante", choices=["a", "b", "scaletta"], action="append")
    ap.add_argument("--quota-destra", type=float, default=1 / 3.0)
    ap.add_argument("--out-dir", default=os.path.dirname(__file__))
    args = ap.parse_args()
    quali = args.variante or ["a", "b", "scaletta"]

    media_mm = 102
    stampabile = CONTINUOUS[media_mm]["print"]

    fogli = []
    if "a" in quali:
        et = render_etichetta(58.9, args.quota_destra)
        fogli.append(("a", con_scaffale(
            et, "A - larghezza 58,9 mm (lettura del canvas) - corpi dichiarati - altezza %.1f mm"
                % (et.size[1] / DOTS_PER_MM), stampabile), False))
    if "b" in quali:
        # piu' larga dell'area stampabile: il foglio si costruisce a misura sua e poi si ruota,
        # come farebbe il renderer sul rotolo da 62
        et = render_etichetta(107.7, args.quota_destra)
        foglio = con_scaffale(
            et, "B - larghezza 107,7 mm (58,9 mm come altezza) - corpi dichiarati - altezza %.1f mm"
                % (et.size[1] / DOTS_PER_MM))
        fogli.append(("b", foglio, True))
    if "scaletta" in quali:
        fogli.append(("scaletta", con_scaffale(
            render_scaletta(), "SCALETTA DEI CORPI - misurare con il righello", stampabile), False))

    pronti = []
    for nome, foglio, ruota in fogli:
        im = foglio
        if ruota:
            # come farebbe il renderer sul rotolo da 62: il testo corre lungo il nastro
            im = foglio.rotate(90, expand=True)
            if im.size[0] > stampabile:
                raise SystemExit("variante %s: dopo la rotazione e' larga %.1f mm, non ci sta "
                                 "nei %.1f mm stampabili" % (nome, im.size[0] / DOTS_PER_MM,
                                                             stampabile / DOTS_PER_MM))
            tela = Image.new("L", (stampabile, im.size[1]), 255)
            tela.paste(im, ((stampabile - im.size[0]) // 2, 0))
            im = tela
        print("%s: %d x %d dot = %.1f x %.1f mm" % (
            nome, im.size[0], im.size[1], im.size[0] / DOTS_PER_MM, im.size[1] / DOTS_PER_MM))
        pronti.append((nome, im))

    for nome, im in pronti:
        percorso = os.path.join(args.out_dir, "prova_%s.png" % nome)
        im.save(percorso)
        print("  anteprima:", percorso)

    if args.render_only:
        return

    p = UsbPrinter()
    drain(p)
    p.write(b"\x1biS")
    d = poll_read(p)
    if len(d) < 32:
        raise SystemExit("la stampante non risponde allo stato")
    s = decode_status(d[:32])
    print("Stato:", fmt_status(d))
    if s["error1"] or s["error2"] or s["media_width_mm"] != media_mm:
        raise SystemExit("serve il nastro continuo da %d mm senza errori" % media_mm)
    for nome, im in pronti:
        print("Stampo", nome, flush=True)
        if not stampa(p, im, media_mm):
            print("  errore durante la stampa, mi fermo")
            break
        time.sleep(1.0)
        drain(p)
    p.close()


if __name__ == "__main__":
    main()
