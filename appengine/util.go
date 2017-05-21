package main

import (
	"io"
	"io/ioutil"
)

// DrainAndClose reads r fully and then close it
func DrainAndClose(r io.ReadCloser) {
	if r == nil {
		return
	}
	io.Copy(ioutil.Discard, r)
	r.Close()
}
