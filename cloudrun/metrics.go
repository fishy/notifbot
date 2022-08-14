package main

import (
	"context"
	"fmt"
	"time"

	monitoring "cloud.google.com/go/monitoring/apiv3/v2"
	metricpb "google.golang.org/genproto/googleapis/api/metric"
	monitoredrespb "google.golang.org/genproto/googleapis/api/monitoredres"
	monitoringpb "google.golang.org/genproto/googleapis/monitoring/v3"
	"google.golang.org/protobuf/types/known/timestamppb"
)

func sendMetrics(
	ctx context.Context,
	client *monitoring.MetricClient,
	path string,
	point *monitoringpb.Point,
	labels map[string]string,
) error {
	// Writes time series data.
	if err := client.CreateTimeSeries(ctx, &monitoringpb.CreateTimeSeriesRequest{
		Name: "projects/" + getProjectID(),
		TimeSeries: []*monitoringpb.TimeSeries{
			{
				Metric: &metricpb.Metric{
					Type:   path,
					Labels: labels,
				},
				Resource: &monitoredrespb.MonitoredResource{
					Type: "global",
					Labels: map[string]string{
						"project_id": getProjectID(),
					},
				},
				Points: []*monitoringpb.Point{point},
			},
		},
	}); err != nil {
		return fmt.Errorf("metrics: failed to write time series data: %w", err)
	}

	return nil
}

func sendMessageMetrics(ctx context.Context, data chatCounterMapType) error {
	if len(data) == 0 {
		return nil
	}

	start := time.Now()
	defer func() {
		l(ctx).Infow(
			"sendMessageMetrics done",
			"size", len(data),
			"took", time.Since(start),
		)
	}()

	// Creates a client.
	client, err := monitoring.NewMetricClient(ctx)
	if err != nil {
		return fmt.Errorf("metrics: failed to create client: %w", err)
	}

	for id, count := range data {
		dataPoint := &monitoringpb.Point{
			Interval: &monitoringpb.TimeInterval{
				EndTime: &timestamppb.Timestamp{
					Seconds: time.Now().Unix(),
				},
			},
			Value: &monitoringpb.TypedValue{
				Value: &monitoringpb.TypedValue_Int64Value{
					Int64Value: count,
				},
			},
		}

		if err := sendMetrics(
			ctx,
			client,
			"custom.googleapis.com/messages/count",
			dataPoint,
			map[string]string{
				"chat_id": fmt.Sprintf("%d", id),
				"from":    "cloudrun",
			},
		); err != nil {
			return err
		}
	}

	// Closes the client and flushes the data to Stackdriver.
	if err := client.Close(); err != nil {
		return fmt.Errorf("metrics: failed to close client: %w", err)
	}

	return nil
}
