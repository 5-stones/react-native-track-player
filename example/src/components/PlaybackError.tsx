import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Event, useTrackPlayerEvents } from 'react-native-track-player';

export const PlaybackError: React.FC = () => {
  const [error, setError] = useState<string | undefined>();

  useTrackPlayerEvents([Event.PlaybackError], (event) => {
    setError(event.error?.message);
  });

  if (!error) return null;

  return (
    <View style={styles.container}>
      <Text style={styles.text}>{error}</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: '100%',
    marginVertical: 24,
    alignSelf: 'center',
  },
  text: {
    color: 'red',
    width: '100%',
    textAlign: 'center',
  },
});
