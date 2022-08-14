package main

import (
	"io"
	"os"
)

func getProjectID() string {
	return os.Getenv("CLOUD_PROJECT_ID")
}

// DrainAndClose reads r fully and then close it
func DrainAndClose(r io.ReadCloser) {
	if r == nil {
		return
	}
	io.Copy(io.Discard, r)
	r.Close()
}

// Update is a update from telegram webhook.
type Update struct {
	ID      int64   `json:"update_id,omitempty"`
	Message Message `json:"message,omitempty"`
}

// Message is a telegram message.
type Message struct {
	ID   int64  `json:"message_id,omitempty"`
	From User   `json:"from,omitempty"`
	Chat Chat   `json:"chat,omitempty"`
	Date int64  `json:"date,omitempty"`
	Text string `json:"text,omitempty"`
}

// User is a telegram user.
type User struct {
	ID        int64  `json:"id,omitempty"`
	FirstName string `json:"first_name,omitempty"`
	LastName  string `json:"last_name,omitempty"`
	Username  string `json:"username,omitempty"`
	Langueage string `json:"language_code,omitempty"`
}

// Chat is a telegram chat.
type Chat struct {
	ID        int64  `json:"id,omitempty"`
	FirstName string `json:"first_name,omitempty"`
	LastName  string `json:"last_name,omitempty"`
	Username  string `json:"username,omitempty"`
	Type      string `json:"type,omitempty"`
}

// ReplyMessage is a message sent on webhook requests.
type ReplyMessage struct {
	Method  string `json:"method,omitempty"`
	ChatID  int64  `json:"chat_id,omitempty"`
	ReplyTo int64  `json:"reply_to_message_id,omitempty"`
	Text    string `json:"text,omitempty"`
}
