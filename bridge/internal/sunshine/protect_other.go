//go:build !windows

package sunshine

// protect stores the login as is; the file is created readable by this user only.
func protect(b []byte) ([]byte, error) { return b, nil }

func unprotect(b []byte) ([]byte, error) { return b, nil }
