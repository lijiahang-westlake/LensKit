# 把 classes.dex 追加进 APK（保留原条目的压缩方式，尤其是 resources.arsc）
import sys, zipfile

src, dst, dex = sys.argv[1], sys.argv[2], sys.argv[3]
zin = zipfile.ZipFile(src, 'r')
zout = zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED)
for item in zin.infolist():
    data = zin.read(item.filename)
    ni = zipfile.ZipInfo(item.filename, date_time=item.date_time)
    ni.compress_type = item.compress_type   # 原样保留（resources.arsc 必须未压缩）
    ni.external_attr = item.external_attr
    zout.writestr(ni, data)
zout.write(dex, 'classes.dex', zipfile.ZIP_DEFLATED)
zout.close()
zin.close()
print('packed:', dst)
