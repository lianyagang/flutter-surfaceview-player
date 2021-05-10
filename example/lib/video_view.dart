import 'package:flutter/cupertino.dart';
import 'package:flutter_surface_plugin/fijkplayer.dart';

class VideoView extends StatefulWidget {
  final String? url;

  const VideoView({Key? key, this.url}) : super(key: key);

  @override
  State createState() {
    return new VideoState();
  }
}

class VideoState extends State<VideoView> {
  final FijkPlayer player = FijkPlayer();
  int _textureId = -1;

  @override
  void initState() {
    super.initState();
    player.setDataSource(widget.url, autoPlay: true);
    _setupTexture();
  }

  @override
  Widget build(BuildContext context) {
    //这里注意Texure是dart提供的控件 参数必须是java Plugin传过来的texureId
    return AspectRatio(
      aspectRatio: 16 / 9,
      child: Texture(textureId: _textureId),
    );
  }

  void _setupTexture() async {
    final int vid = await player.setupSurface() ?? -1;
    FijkLog.i("view setup, vid:" + vid.toString());
    print('i滴滴滴滴${vid.toString()}');
    if (mounted) {
      setState(() {
        _textureId = vid;
      });
    }
  }
}
