const resolveAssetResource = (base64: any) => {
  if (/^https?:\/\//.test(base64)) {
    return base64;
  }

  // strip tag png
  if (base64.indexOf ('data:image/png;') >= 0) {
    base64 = base64.substring(base64.indexOf(',') + 1)

    const header = atob(base64.slice(0, 50)).slice(16, 24)
    const uint8 = Uint8Array.from(header, c => c.charCodeAt(0))
    const dataView = new DataView(uint8.buffer)

    return {
      width: dataView.getInt32(0),
      height: dataView.getInt32(4)
    }
  }

  // strip tag for jpeg
  if (base64.indexOf ('data:image/jpeg;') >= 0) {
    base64 = base64.substring(base64.indexOf(',') + 1)

    let bindata = atob (base64);
    const data = Uint8Array.from(bindata, c => c.charCodeAt(0))

    var off = 0;
    while (off < data.length) {

      while (data [off] == 0xff) off++;
      var mrkr = data [off];  off++;

      if (mrkr == 0xd8) continue;    // SOI
      if (mrkr == 0xd9) break;       // EOI
      if (0xd0 <= mrkr && mrkr <= 0xd7) continue;
      if (mrkr == 0x01) continue;    // TEM

      var len = (data [off] << 8) | data [off + 1];
      off += 2;

      if(mrkr == 0xc0) {
        return {
          type        : 'jpeg',
          bpc         : data [off],     // precission (bits per channel)
          width       : (data [off + 1]<<8) | data [off + 2],
          height      : (data [off + 3]<<8) | data [off + 4],
          cps         : data [off + 5]    // number of color components
        }
      }
      off+=len-2;
    }
  }
}

export default resolveAssetResource;
