package main

import (
	"context"
	"errors"
	"fmt"

	"cloud.google.com/go/datastore"
	"google.golang.org/appengine/v2/memcache"
)

const (
	chatKey     = "chat-%d"
	chatKind    = "notifbotchats"
	tokenLength = 16
)

// EntityChat is the entity of a chat/token stored in datastore.
type EntityChat struct {
	Chat  int64  `datastore:"chat"`
	Token string `datastore:"token"`
}

func (e *EntityChat) getKey() string {
	return fmt.Sprintf(chatKey, e.Chat)
}

func (e *EntityChat) datastoreKey() *datastore.Key {
	return datastore.NameKey(chatKind, e.getKey(), nil)
}

// SaveMemcache saves this token into memcache.
func (e *EntityChat) SaveMemcache(ctx context.Context) error {
	return memcache.Set(ctx, &memcache.Item{
		Key:   e.getKey(),
		Value: []byte(e.Token),
	})
}

// GetURL returns the url for this chat.
func (e *EntityChat) GetURL() string {
	return fmt.Sprintf(
		"%s%s%d/%s",
		globalURLPrefix,
		clientPrefix,
		e.Chat,
		e.Token,
	)
}

// GetChat gets a chat token from db.
func GetChat(ctx context.Context, id int64) *EntityChat {
	e := &EntityChat{
		Chat: id,
	}
	value, err := memcache.Get(ctx, e.getKey())
	if err == nil {
		e.Token = string(value.Value)
		return e
	}
	if !errors.Is(err, memcache.ErrCacheMiss) {
		l(ctx).Errorw(
			"Failed to get memcache key",
			"key", e.getKey(),
			"err", err,
		)
	}
	key := e.datastoreKey()
	if err := dsClient.Get(ctx, key, e); err != nil {
		logger := l(ctx).Errorw
		if errors.Is(err, datastore.ErrNoSuchEntity) {
			logger = l(ctx).Infow
		}
		logger(
			"Failed to get datastore key",
			"key", key,
			"err", err,
		)
		return nil
	}
	if err := e.SaveMemcache(ctx); err != nil {
		l(ctx).Errorw(
			"Failed to save memcache key",
			"key", e.getKey(),
			"err", err,
		)
	}
	return e
}
