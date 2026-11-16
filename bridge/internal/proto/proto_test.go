package proto

import (
	"bytes"
	"errors"
	"strings"
	"testing"
)

func TestRoundTrip(t *testing.T) {
	var buf bytes.Buffer
	in := []Message{
		{T: TypeHello, Token: "abc", Name: "Galaxy"},
		{T: TypeMove, DX: 3.5, DY: -2},
		{T: TypeKey, Key: "Z", Mods: []string{"ctrl"}},
		{T: TypeText, Text: "héllo, wörld\n"},
	}
	for _, m := range in {
		if err := Write(&buf, m); err != nil {
			t.Fatal(err)
		}
	}
	r := NewReader(&buf)
	for i, want := range in {
		got, err := r.Read()
		if err != nil {
			t.Fatalf("message %d: %v", i, err)
		}
		if got.T != want.T || got.Text != want.Text || got.DX != want.DX || got.Key != want.Key {
			t.Fatalf("message %d: got %+v want %+v", i, got, want)
		}
	}
	if _, err := r.Read(); !errors.Is(err, ErrClosed) {
		t.Fatalf("expected ErrClosed, got %v", err)
	}
}

func TestRejectsGarbage(t *testing.T) {
	r := NewReader(strings.NewReader("not json\n"))
	if _, err := r.Read(); err == nil {
		t.Fatal("expected error")
	}
	r = NewReader(strings.NewReader(`{"dx":1}` + "\n"))
	if _, err := r.Read(); err == nil {
		t.Fatal("expected missing-type error")
	}
}

func TestRejectsHugeLine(t *testing.T) {
	r := NewReader(strings.NewReader(`{"t":"text","text":"` + strings.Repeat("a", MaxLine) + `"}` + "\n"))
	if _, err := r.Read(); err == nil {
		t.Fatal("expected error for oversized message")
	}
}
