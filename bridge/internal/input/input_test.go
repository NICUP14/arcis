package input

import "testing"

func TestNormalizeKey(t *testing.T) {
	cases := map[string]string{
		"enter": "Enter", "BACKSPACE": "Backspace", "z": "Z", "7": "7", "pageup": "PageUp",
		"meta": "Meta", "equal": "Equal", "MINUS": "Minus",
		"?": "", "F13": "", "": "",
	}
	for in, want := range cases {
		if got := NormalizeKey(in); got != want {
			t.Errorf("NormalizeKey(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestSplitScrollKeepsRemainder(t *testing.T) {
	var s SplitScroll
	x, y := s.Add(0.4, 0.6)
	if x != 0 || y != 0 {
		t.Fatalf("got %d,%d", x, y)
	}
	x, y = s.Add(0.4, 0.6)
	if x != 0 || y != 1 {
		t.Fatalf("got %d,%d", x, y)
	}
	x, y = s.Add(0.3, -2.5)
	if x != 1 || y != -2 {
		t.Fatalf("got %d,%d", x, y)
	}
}

func TestHasMod(t *testing.T) {
	if !HasMod([]string{"Ctrl", "shift"}, "ctrl") || HasMod(nil, "alt") {
		t.Fatal("HasMod wrong")
	}
}
