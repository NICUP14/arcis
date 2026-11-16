// Package mic plays the phone's microphone audio into a virtual microphone on this
// computer, so apps (Discord, Teams, Zoom...) can use the phone as a headset mic.
//
// Audio format on the wire: 16 kHz, mono, signed 16-bit little-endian PCM.
package mic

// SampleRate of the PCM audio sent by phones.
const SampleRate = 16000

// Sink accepts PCM audio for the virtual microphone.
type Sink interface {
	// Write plays a chunk of PCM (16 kHz mono s16le).
	Write(pcm []byte) error
	// Name describes where the audio goes (shown in logs / status page).
	Name() string
	Close() error
}
