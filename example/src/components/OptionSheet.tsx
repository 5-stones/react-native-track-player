import SegmentedControl from '@react-native-segmented-control/segmented-control';
import React, { useState } from 'react';
import { Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import TrackPlayer, {
  AppKilledPlaybackBehavior,
  RepeatMode,
} from 'react-native-track-player';
import { playerOptions } from '../services';
import { Spacer } from './Spacer';

export function OptionSheet() {
  return (
    <ScrollView contentContainerStyle={styles.contentContainer}>
      <Options
        label="Repeat Mode"
        options={[
          { label: 'Off', value: RepeatMode.Off },
          { label: 'Track', value: RepeatMode.Track },
          { label: 'Queue', value: RepeatMode.Queue },
        ]}
        initialValue={TrackPlayer.getRepeatMode()}
        onSelect={(repeatMode) => {
          TrackPlayer.setRepeatMode(repeatMode);
        }}
      />
      <Spacer />
      {Platform.OS === 'android' && (
        <Options
          label="Audio Service on App Kill"
          options={[
            {
              label: 'Continue',
              value: AppKilledPlaybackBehavior.ContinuePlayback,
            },
            { label: 'Pause', value: AppKilledPlaybackBehavior.PausePlayback },
            {
              label: 'Stop & Remove',
              value:
                AppKilledPlaybackBehavior.StopPlaybackAndRemoveNotification,
            },
          ]}
          initialValue={playerOptions.android.appKilledPlaybackBehavior}
          onSelect={async (appKilledPlaybackBehavior) => {
            // TODO: Copied from example/src/services/SetupService.tsx until updateOptions
            // allows for partial updates (i.e. only android.appKilledPlaybackBehavior).
            await TrackPlayer.updateOptions({
              ...playerOptions,
              android: {
                ...playerOptions.android,
                appKilledPlaybackBehavior,
              },
            });
          }}
        />
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  contentContainer: {
    padding: 16,
  },
  optionRow: {
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  optionColumn: {
    width: '100%',
    flexDirection: 'column',
  },
  optionRowLabel: {
    color: 'white',
    fontSize: 20,
    fontWeight: '600',
  },
});

function OptionStack({
  children,
  vertical,
}: {
  children: React.ReactNode;
  vertical?: boolean;
}) {
  const childrenArray = React.Children.toArray(children);

  return (
    <View style={vertical ? styles.optionColumn : styles.optionRow}>
      {childrenArray.map((child, index) => (
        <View key={index}>{child}</View>
      ))}
    </View>
  );
}

function Options<T>({
  label,
  options,
  initialValue,
  onSelect,
}: {
  label: string;
  options: Array<{ label: string; value: T }>;
  initialValue: T;
  onSelect: (value: T) => void;
}) {
  const [selectedIndex, setSelectedIndex] = useState(() =>
    options.findIndex((opt) => opt.value === initialValue)
  );

  return (
    <OptionStack vertical={true}>
      <Text style={styles.optionRowLabel}>{label}</Text>
      <Spacer />
      <SegmentedControl
        appearance={'dark'}
        values={options.map((opt) => opt.label)}
        selectedIndex={selectedIndex}
        onChange={(event) => {
          const index = event.nativeEvent.selectedSegmentIndex;
          setSelectedIndex(index);
          const value = options[index]?.value;
          if (value !== undefined) {
            onSelect(value);
          }
        }}
      />
    </OptionStack>
  );
}
