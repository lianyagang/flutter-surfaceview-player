import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_surface_plugin/fijk2player.dart';
/// https://fijkplayer.befovy.com/docs/zh/custom-ui.html#%E6%97%A0%E7%8A%B6%E6%80%81-ui-
Widget simplestUI(
    FijkPlayer player, BuildContext context, Size viewSize, Rect texturePos) {
  Rect rect = Rect.fromLTRB(
      max(0.0, texturePos.left),
      max(0.0, texturePos.top),
      min(viewSize.width, texturePos.right),
      min(viewSize.height, texturePos.bottom));
  bool isPlaying = player.state == FijkState.started;
  return Positioned.fromRect(
    rect: rect,
    child: Container(
      alignment: Alignment.bottomLeft,
      child: IconButton(
        icon: Icon(
          isPlaying ? Icons.pause : Icons.play_arrow,
          color: Colors.white,
        ),
        onPressed: () {
          isPlaying ? player.pause() : player.start();
        },
      ),
    ),
  );
}