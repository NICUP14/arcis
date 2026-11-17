package sunshine

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
)

// Login is the Sunshine web UI username and password.
type Login struct {
	User string `json:"user"`
	Pass string `json:"pass"`
}

const loginFile = "sunshine.json"

// LoadLogin reads the stored login; zero Login if none.
func LoadLogin(dir string) (Login, error) {
	b, err := os.ReadFile(filepath.Join(dir, loginFile))
	if errors.Is(err, os.ErrNotExist) {
		return Login{}, nil
	}
	if err != nil {
		return Login{}, err
	}
	plain, err := unprotect(b)
	if err != nil {
		return Login{}, err
	}
	var l Login
	err = json.Unmarshal(plain, &l)
	return l, err
}

// SaveLogin stores the login, encrypted for this Windows user (DPAPI); on other systems
// it relies on the file being readable by this user only.
func SaveLogin(dir string, l Login) error {
	plain, err := json.Marshal(l)
	if err != nil {
		return err
	}
	b, err := protect(plain)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(dir, loginFile), b, 0o600)
}
