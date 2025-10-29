import { Children, type ReactNode } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import TrackPlayer, {
  AppKilledPlaybackBehavior,
  RepeatMode,
  useOptions,
  useRepeatMode,
} from 'react-native-track-player';
import SegmentedControl from './SegmentedControl';
import { Spacer } from './Spacer';

export function OptionSheet() {
  const currentOptions = useOptions();
  const currentRepeatMode = useRepeatMode();
  return (
    <ScrollView contentContainerStyle={styles.contentContainer}>
      <Options
        label="Repeat Mode"
        options={[
          { label: 'Off', value: RepeatMode.Off },
          { label: 'Track', value: RepeatMode.Track },
          { label: 'Queue', value: RepeatMode.Queue },
        ]}
        value={currentRepeatMode}
        onSelect={(repeatMode) => {
          TrackPlayer.setRepeatMode(repeatMode);
        }}
      />
      <Spacer />
      {currentOptions.android && (
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
          value={
            currentOptions.android.appKilledPlaybackBehavior
          }
          onSelect={(appKilledPlaybackBehavior) => {
            TrackPlayer.updateOptions({
              android: {
                appKilledPlaybackBehavior,
              },
            });
          }}
        />
      )}
      <Spacer />
      <Options
        label="Jump Interval"
        options={[
          { label: '5s', value: 5 },
          { label: '10s', value: 10 },
          { label: '15s', value: 15 },
          { label: '30s', value: 30 },
        ]}
        value={currentOptions.backwardJumpInterval}
        onSelect={(jumpInterval) => {
          TrackPlayer.updateOptions({
            backwardJumpInterval: jumpInterval,
            forwardJumpInterval: jumpInterval,
          });
        }}
      />
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
  children: ReactNode;
  vertical?: boolean;
}) {
  const childrenArray = Children.toArray(children);

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
  value,
  onSelect,
}: {
  label: string;
  options: Array<{ label: string; value: T }>;
  value: T;
  onSelect: (value: T) => void;
}) {
  const selectedIndex = options.findIndex((opt) => opt.value === value);

  return (
    <OptionStack vertical={true}>
      <Text style={styles.optionRowLabel}>{label}</Text>
      <Spacer />
      <SegmentedControl
        appearance={'dark'}
        values={options.map((opt) => opt.label)}
        selectedIndex={selectedIndex}
        onChange={(index) => {
          const newValue = options[index]?.value;
          if (newValue !== undefined) {
            onSelect(newValue);
          }
        }}
      />
    </OptionStack>
  );
}
