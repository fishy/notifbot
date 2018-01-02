package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"strconv"

	"google.golang.org/appengine/datastore"
	"google.golang.org/appengine/log"
	"google.golang.org/appengine/memcache"
)

const (
	memcacheChatPrefix = "chat-"
	chatKind           = "notifbotchats"
	tokenLength        = 16
)

// EntityChat is the entity of a chat/token stored in datastore.
type EntityChat struct {
	Chat  int64  `datastore:"chat"`
	Token string `datastore:"token"`
}

func (e *EntityChat) memcacheKey() string {
	return memcacheChatPrefix + strconv.FormatInt(e.Chat, 10)
}

// SaveMemcache saves this token into memcache.
func (e *EntityChat) SaveMemcache(ctx context.Context) error {
	return memcache.Set(ctx, &memcache.Item{
		Key:   e.memcacheKey(),
		Value: []byte(e.Token),
	})
}

func (e *EntityChat) datastoreKey(ctx context.Context) *datastore.Key {
	return datastore.NewKey(ctx, chatKind, e.memcacheKey(), 0, nil)
}

// SaveDatastore saves this token into datastore.
func (e *EntityChat) SaveDatastore(ctx context.Context) error {
	key := e.datastoreKey(ctx)
	_, err := datastore.Put(ctx, key, e)
	return err
}

// Delete deletes this chat token from both memcache and datastore.
func (e *EntityChat) Delete(ctx context.Context) {
	memKey := e.memcacheKey()
	if err := memcache.Delete(ctx, memKey); err != nil {
		log.Errorf(ctx, "failed to delete memcache key %s: %v", memKey, err)
	}
	key := e.datastoreKey(ctx)
	if err := datastore.Delete(ctx, key); err != nil {
		log.Errorf(ctx, "failed to delete datastore key %v: %v", key, err)
	}
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
	memKey := e.memcacheKey()
	item, err := memcache.Get(ctx, memKey)
	if err == nil {
		e.Token = string(item.Value)
		return e
	}
	if err != memcache.ErrCacheMiss {
		log.Errorf(ctx, "failed to get memcache key %s: %v", memKey, err)
	}
	key := e.datastoreKey(ctx)
	if err := datastore.Get(ctx, key, e); err != nil {
		log.Errorf(ctx, "failed to get datastore key %v: %v", key, err)
		return nil
	}
	if err := e.SaveMemcache(ctx); err != nil {
		log.Errorf(ctx, "failed to save memcache key %s: %v", memKey, err)
	}
	return e
}

// NewChat creates a new chat token and saves it into db.
func NewChat(ctx context.Context, id int64) *EntityChat {
	if e := GetChat(ctx, id); e != nil {
		return e
	}
	e := &EntityChat{
		Chat:  id,
		Token: randomString(ctx, tokenLength),
	}
	if err := e.SaveMemcache(ctx); err != nil {
		log.Errorf(ctx, "failed to save memcache chat %d: %v", id, err)
	}
	if err := e.SaveDatastore(ctx); err != nil {
		log.Errorf(ctx, "failed to save datastore chat %d: %v", id, err)
	}
	return e
}

func randomString(ctx context.Context, len int) string {
	buf := make([]byte, len)
	n, err := rand.Read(buf)
	if err != nil || n != len {
		log.Errorf(
			ctx,
			"failed to generate random string: read %d/%d, err = %v",
			n,
			len,
			err,
		)
	}
	return hex.EncodeToString(buf)
}
