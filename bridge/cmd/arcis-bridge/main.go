// Command arcis-bridge runs on a host (PC) and performs the input the Arcis
// phone app sends it: pointer movement, clicks, scrolling, text, and more.
package main

import (
	"flag"
	"fmt"
)

// Version is the bridge version, overridable at build time with -ldflags.
var Version = "0.1.0"

func main() {
	showVersion := flag.Bool("version", false, "print version and exit")
	flag.Parse()
	if *showVersion {
		fmt.Println("arcis-bridge", Version)
		return
	}
	fmt.Println("arcis-bridge", Version, "- not implemented yet")
}
