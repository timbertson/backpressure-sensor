<img src="http://gfxmonk.net/dist/status/project/backpressure-sensor.png">

# Backpressure-sensor

For an introduction to this library, see [the accompanying blog post](https://zendesk.engineering/event-processing-pipelines-observing-and-optimising-part-2-8cf044ae754b)

## Chunking

To reduce overheads, the FS2 backend (starting in v0.7) measures timings between chunks instead of individual elements. The wait time / variance metrics should be interpreted to mean "per chunk" rather than "per element" when using this backend. To help understand throughput, there's an explicit `.count` metric emitted which tracks the number of elements explicitly, regardless of chunk size.
