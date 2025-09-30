import BottomSheet from '@gorhom/bottom-sheet';
import { useCallback, useEffect, useMemo, useRef } from 'react';
import {
  ActivityIndicator,
  Dimensions,
  Linking,
  Platform,
  StatusBar,
  StyleSheet,
  View,
} from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { useActiveTrack } from 'react-native-track-player';
import {
  ActionSheet,
  Button,
  OptionSheet,
  PlayerControls,
  Progress,
  Spacer,
  TrackInfo,
} from './components';
import { useSetupPlayer } from './hooks/usePlayer';

export default function App() {
  const isPlayerReady = useSetupPlayer();
  return (
    <SafeAreaProvider>
      <GestureHandlerRootView style={styles.gestureContainer}>
        {isPlayerReady ? (
          <Player />
        ) : (
          <SafeAreaView style={styles.screenContainer}>
            <ActivityIndicator />
          </SafeAreaView>
        )}
      </GestureHandlerRootView>
    </SafeAreaProvider>
  );
}

function Player() {
  const track = useActiveTrack();
  // options bottom sheet
  const optionsSheetRef = useRef<BottomSheet>(null);
  const optionsSheetSnapPoints = useMemo(() => ['40%'], []);
  const handleOptionsPress = useCallback(() => {
    optionsSheetRef.current?.snapToIndex(0);
  }, [optionsSheetRef]);

  // actions bottom sheet
  const actionsSheetRef = useRef<BottomSheet>(null);
  const actionsSheetSnapPoints = useMemo(() => ['40%'], []);
  const handleActionsPress = useCallback(() => {
    actionsSheetRef.current?.snapToIndex(0);
  }, [actionsSheetRef]);

  useEffect(() => {
    function deepLinkHandler(data: { url: string }) {
      console.log('deepLinkHandler', data.url);
    }

    // This event will be fired when the app is already open and the notification is clicked
    const subscription = Linking.addEventListener('url', deepLinkHandler);

    // When you launch the closed app from the notification or any other link
    Linking.getInitialURL().then((url) => console.log('getInitialURL', url));

    return () => {
      subscription.remove();
    };
  }, []);

  return (
    <SafeAreaView style={styles.screenContainer}>
      <StatusBar barStyle={'light-content'} />
      <View style={styles.contentContainer}>
        <View style={styles.topBarContainer}>
          <Button title="Options" onPress={handleOptionsPress} type="primary" />
          <Button title="Actions" onPress={handleActionsPress} type="primary" />
        </View>
        <TrackInfo track={track} />
        <Progress live={track?.isLiveStream} />
        <Spacer />
        <PlayerControls />
        <Spacer mode={'expand'} />
      </View>
      <BottomSheet
        index={-1}
        ref={optionsSheetRef}
        enablePanDownToClose={true}
        snapPoints={optionsSheetSnapPoints}
        handleIndicatorStyle={styles.sheetHandle}
        backgroundStyle={styles.sheetBackgroundContainer}
      >
        <OptionSheet />
      </BottomSheet>
      <BottomSheet
        index={-1}
        ref={actionsSheetRef}
        enablePanDownToClose={true}
        snapPoints={actionsSheetSnapPoints}
        handleIndicatorStyle={styles.sheetHandle}
        backgroundStyle={styles.sheetBackgroundContainer}
      >
        <ActionSheet />
      </BottomSheet>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  gestureContainer: { flex: 1 },
  screenContainer: {
    flex: 1,
    backgroundColor: '#212121',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: Platform.OS === 'web' ? Dimensions.get('window').height : '100%',
  },
  contentContainer: {
    flex: 1,
    alignItems: 'center',
  },
  topBarContainer: {
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'flex-end',
  },
  sheetBackgroundContainer: {
    backgroundColor: '#181818',
  },
  sheetHandle: {
    backgroundColor: 'white',
  },
});
