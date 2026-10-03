// Converte una foto scelta (fotocamera o galleria) in un JPEG ridimensionato
// prima di mandarla al servizio (docs/api.md, "Foto dei lotti e dei
// documenti": accetta solo JPEG o PNG fino a 8 MB) - deciso da Gianluca, 25
// settembre 2026: una foto di fotocamera puo' pesare diversi MB, e la
// galleria del telefono puo' dare WebP o HEIC, che il servizio non
// riconosce. Si ridimensiona al lato lungo massimo (LATO_MASSIMO_PX) e si
// ricomprime in JPEG (QUALITA_JPEG): qualunque formato il browser sa
// decodificare arriva cosi' sempre sotto il limite, senza dover scegliere a
// mano un formato giusto.
//
// L'orientamento EXIF (una foto scattata in verticale che l'apparecchio
// scrive "sdraiata" con un tag di rotazione) si applica qui con
// "imageOrientation: from-image": il servizio lo fa gia' per i JPEG che
// riceve senza passare da qui (vedi sopra), ma un file ricompresso in questo
// modulo perde quel tag - va applicato PRIMA di ricomprimere, o la foto
// uscirebbe storta.
//
// Se la conversione fallisce per qualunque motivo (il browser non sa
// decodificare il formato - capita con l'HEIC di alcuni telefoni Android/
// Chrome, che non hanno il decodificatore) si manda il file originale cosi'
// com'e': il servizio lo accetta o lo rifiuta come faceva prima di questo
// modulo, niente di peggio di oggi.
const LATO_MASSIMO_PX = 2000;
const QUALITA_JPEG = 0.85;

// Safari prima del 17 non conosce il valore "from-image" e risponde con un
// TypeError: si riprova senza opzioni (li' l'orientamento EXIF lo applica gia'
// da solo il decodificatore), invece di mandare la foto originale, magari da
// 10 MB, che il servizio rifiuterebbe.
async function decodifica(file: File): Promise<ImageBitmap> {
  try {
    return await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch (errore) {
    if (errore instanceof TypeError) return createImageBitmap(file);
    throw errore;
  }
}

export async function convertiInJpeg(file: File): Promise<File> {
  try {
    const bitmap = await decodifica(file);
    try {
      const scala = Math.min(1, LATO_MASSIMO_PX / Math.max(bitmap.width, bitmap.height));
      const larghezza = Math.round(bitmap.width * scala);
      const altezza = Math.round(bitmap.height * scala);
      const tela = document.createElement("canvas");
      tela.width = larghezza;
      tela.height = altezza;
      const contesto = tela.getContext("2d");
      if (!contesto) return file;
      contesto.drawImage(bitmap, 0, 0, larghezza, altezza);
      const blob = await new Promise<Blob | null>((risolvi) => tela.toBlob(risolvi, "image/jpeg", QUALITA_JPEG));
      if (!blob) return file;
      const nome = file.name.replace(/\.[^.]+$/, "") + ".jpg";
      return new File([blob], nome, { type: "image/jpeg" });
    } finally {
      bitmap.close();
    }
  } catch {
    return file;
  }
}
