package nomad.api;

public interface NomadWorkTick extends NomadTableTick {

  String getWorkId();

  float getSpace();

  float getStaff();

  int getSize();
}
