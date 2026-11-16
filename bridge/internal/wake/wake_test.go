package wake

import (
	"bytes"
	"testing"
)

func TestMagicPacket(t *testing.T) {
	p, err := MagicPacket("aa:bb:cc:dd:ee:ff")
	if err != nil {
		t.Fatal(err)
	}
	if len(p) != 102 || !bytes.Equal(p[:6], bytes.Repeat([]byte{0xFF}, 6)) {
		t.Fatalf("bad header: %x", p[:12])
	}
	mac := []byte{0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff}
	for i := 0; i < 16; i++ {
		if !bytes.Equal(p[6+i*6:12+i*6], mac) {
			t.Fatalf("repetition %d wrong", i)
		}
	}
	if _, err := MagicPacket("not-a-mac"); err == nil {
		t.Fatal("expected error")
	}
}
