// Package sunshine talks to Sunshine's local web API so the bridge can finish
// Moonlight pairing by itself: the phone reads the PIN Moonlight shows and sends it
// here, and the bridge submits it like the "PIN" page of Sunshine's web UI would.
package sunshine

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"strings"
	"time"
)

// DefaultURL is Sunshine's web UI / API on this computer.
const DefaultURL = "https://localhost:47990"

var (
	// ErrNoLogin: no Sunshine login is stored in the bridge yet.
	ErrNoLogin = errors.New("set your Sunshine login in the Arcis Bridge (tray › Setup…)")
	// ErrBadLogin: Sunshine refused the stored login.
	ErrBadLogin = errors.New("Sunshine refused the login stored in the Arcis Bridge (tray › Setup…)")
	// ErrNotRunning: nothing answers at Sunshine's address.
	ErrNotRunning = errors.New("Sunshine isn't running on this computer")
	// ErrNoRequest: Sunshine has no pairing request waiting.
	ErrNoRequest = errors.New("Sunshine has no pairing request from Moonlight")
	// ErrPINRefused: Sunshine rejected the PIN.
	ErrPINRefused = errors.New("Sunshine didn't accept the PIN")
)

// Client is a Sunshine API client for one login.
type Client struct {
	URL        string
	User, Pass string
	HTTP       *http.Client
	// PendingWait is how long SubmitPIN waits for Moonlight's request to show up.
	PendingWait time.Duration
}

// New returns a client for Sunshine on this computer. Its certificate is self-signed,
// so it isn't checked (the connection never leaves the computer).
func New(url, user, pass string) *Client {
	return &Client{
		URL: strings.TrimRight(url, "/"), User: user, Pass: pass,
		HTTP: &http.Client{
			Timeout:   8 * time.Second,
			Transport: &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}}, //nolint:gosec // localhost
		},
		PendingWait: 6 * time.Second,
	}
}

func (c *Client) do(ctx context.Context, method, path string, body any, out any) error {
	if c.User == "" {
		return ErrNoLogin
	}
	var rd *bytes.Reader
	if body != nil {
		b, _ := json.Marshal(body)
		rd = bytes.NewReader(b)
	} else {
		rd = bytes.NewReader(nil)
	}
	req, err := http.NewRequestWithContext(ctx, method, c.URL+path, rd)
	if err != nil {
		return err
	}
	req.SetBasicAuth(c.User, c.Pass)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := c.HTTP.Do(req)
	if err != nil {
		var op *net.OpError
		if errors.As(err, &op) && op.Op == "dial" {
			return ErrNotRunning
		}
		return fmt.Errorf("Sunshine: %w", err)
	}
	defer resp.Body.Close()
	switch {
	case resp.StatusCode == http.StatusUnauthorized:
		return ErrBadLogin
	case resp.StatusCode == http.StatusNotFound:
		return errNotFound
	case resp.StatusCode >= 300:
		return fmt.Errorf("Sunshine answered %s", resp.Status)
	}
	if out != nil {
		return json.NewDecoder(resp.Body).Decode(out)
	}
	return nil
}

var errNotFound = errors.New("not found")

// Check verifies that Sunshine is running and accepts the login.
func (c *Client) Check(ctx context.Context) error {
	var cfg map[string]any
	return c.do(ctx, http.MethodGet, "/api/config", nil, &cfg)
}

// pending is a pairing request waiting in Sunshine (newer versions list them).
type pending struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Address string `json:"address"`
}

// SubmitPIN completes a Moonlight pairing request with the PIN Moonlight shows.
// [fromAddr] (the phone's address) picks the right request if several are waiting.
// [name] is how the device will be listed in Sunshine.
func (c *Client) SubmitPIN(ctx context.Context, pin, name, fromAddr string) error {
	body := map[string]string{"pin": pin, "name": name}
	id, err := c.waitForRequest(ctx, fromAddr)
	switch {
	case errors.Is(err, errNotFound):
		// Older Sunshine: no list of requests, the PIN goes to the only one.
	case err != nil:
		return err
	default:
		body["pairing_id"] = id
	}
	var res struct {
		Status any    `json:"status"`
		Error  string `json:"error"`
	}
	if err := c.do(ctx, http.MethodPost, "/api/pin", body, &res); err != nil {
		return err
	}
	if !truthy(res.Status) {
		if res.Error != "" {
			return fmt.Errorf("%w: %s", ErrPINRefused, res.Error)
		}
		return ErrPINRefused
	}
	return nil
}

// waitForRequest returns the id of the waiting request (from [fromAddr] if several),
// polling briefly: Moonlight shows its PIN at about the same moment it asks Sunshine.
func (c *Client) waitForRequest(ctx context.Context, fromAddr string) (string, error) {
	deadline := time.Now().Add(c.PendingWait)
	for {
		var list struct {
			Pairings *[]pending `json:"pairings"`
		}
		if err := c.do(ctx, http.MethodGet, "/api/pin", nil, &list); err != nil {
			return "", err
		}
		if list.Pairings == nil {
			return "", errNotFound
		}
		if id := pick(*list.Pairings, fromAddr); id != "" {
			return id, nil
		}
		if time.Now().After(deadline) {
			return "", ErrNoRequest
		}
		select {
		case <-ctx.Done():
			return "", ctx.Err()
		case <-time.After(400 * time.Millisecond):
		}
	}
}

// pick chooses the request from [fromAddr], or the only one, or the newest (last).
func pick(list []pending, fromAddr string) string {
	if len(list) == 0 {
		return ""
	}
	for _, p := range list {
		if fromAddr != "" && sameIP(p.Address, fromAddr) {
			return p.ID
		}
	}
	return list[len(list)-1].ID
}

func sameIP(a, b string) bool {
	pa, pb := net.ParseIP(strings.Trim(a, "[]")), net.ParseIP(strings.Trim(b, "[]"))
	return pa != nil && pb != nil && pa.Equal(pb)
}

func truthy(v any) bool {
	switch x := v.(type) {
	case bool:
		return x
	case string:
		return x == "true"
	}
	return false
}
