package main

import (
	"context"
	"crypto/sha512"
	"encoding/base64"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

const (
	urlPrefix           = "https://api.telegram.org/bot"
	webhookMaxConn      = 5
	postFormContentType = "application/x-www-form-urlencoded"
)

var tokenValue atomic.Value

var httpClient http.Client

type telegramToken struct {
	Token string

	hashOnce   sync.Once
	hashPrefix string
}

func (bot *telegramToken) String() string {
	return bot.Token
}

func (bot *telegramToken) getURL(endpoint string) string {
	return fmt.Sprintf("%s%s/%s", urlPrefix, bot.String(), endpoint)
}

// PostRequest use POST method to send a request to telegram
func (bot *telegramToken) PostRequest(
	ctx context.Context, endpoint string, params url.Values,
) (code int) {
	start := time.Now()
	defer func() {
		l(ctx).Infow(
			"PostRequest: HTTP POST done",
			"endpoint", endpoint,
			"took", time.Since(start),
		)
	}()

	req, err := http.NewRequest(
		http.MethodPost,
		bot.getURL(endpoint),
		strings.NewReader(params.Encode()),
	)
	if err != nil {
		l(ctx).Errorw(
			"Failed to construct http request",
			"err", err,
		)
		return
	}
	req.Header.Set("Content-Type", postFormContentType)
	resp, err := httpClient.Do(req.WithContext(ctx))
	if resp != nil && resp.Body != nil {
		defer DrainAndClose(resp.Body)
	}
	if err != nil {
		l(ctx).Errorw(
			"PostRequest failed",
			"endpoint", endpoint,
			"err", err,
		)
		return
	}
	if resp.StatusCode != http.StatusOK {
		buf, _ := ioutil.ReadAll(resp.Body)
		l(ctx).Errorw(
			"PostRequest got non-200",
			"endpoint", endpoint,
			"code", resp.StatusCode,
			"body", buf,
		)
	}
	return resp.StatusCode
}

// SendMessage sents a telegram messsage.
func (bot *telegramToken) SendMessage(
	ctx context.Context, id int64, msg string,
) int {
	values := url.Values{}
	values.Add("chat_id", fmt.Sprintf("%d", id))
	values.Add("text", msg)
	return bot.PostRequest(ctx, "sendMessage", values)
}

func (bot *telegramToken) initHashPrefix(ctx context.Context) {
	bot.hashOnce.Do(func() {
		hash := sha512.Sum512_224([]byte(bot.String()))
		bot.hashPrefix = webhookPrefix + base64.URLEncoding.EncodeToString(hash[:])
		l(ctx).Infof("hashPrefix == %s", bot.hashPrefix)
	})
}

func (bot *telegramToken) getWebhookURL(ctx context.Context) string {
	bot.initHashPrefix(ctx)
	return fmt.Sprintf("%s%s", globalURLPrefix, bot.hashPrefix)
}

// ValidateWebhookURL validates whether requested URI in request matches hash
// path.
func (bot *telegramToken) ValidateWebhookURL(r *http.Request) bool {
	bot.initHashPrefix(r.Context())
	return r.URL.Path == bot.hashPrefix
}

// SetWebhook sets webhook with telegram.
func (bot *telegramToken) SetWebhook(ctx context.Context) int {
	bot.initHashPrefix(ctx)

	values := url.Values{}
	values.Add("url", bot.getWebhookURL(ctx))
	values.Add("max_connections", fmt.Sprintf("%d", webhookMaxConn))
	return bot.PostRequest(ctx, "setWebhook", values)
}

// initBot initializes botToken.
func initBot(ctx context.Context) {
	secret, err := getSecret(ctx, tokenID)
	if err != nil {
		l(ctx).Errorw(
			"Failed to get token secret",
			"err", err,
		)
	}
	tokenValue.Store(&telegramToken{
		Token: secret,
	})
	getToken().SetWebhook(ctx)
}

func getToken() *telegramToken {
	return tokenValue.Load().(*telegramToken)
}
