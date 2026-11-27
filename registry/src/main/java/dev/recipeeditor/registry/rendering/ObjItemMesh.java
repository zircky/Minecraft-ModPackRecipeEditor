package dev.recipeeditor.registry.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.io.SafeFiles;
import dev.recipeeditor.registry.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Single-texture Forge OBJ subset used by inventory models. */
public record ObjItemMesh(ResourceSource texture, List<Triangle> triangles) {
    public record Vertex(double x,double y,double z,double u,double v) {}
    public record Triangle(Vertex a,Vertex b,Vertex c) {}
    private record UV(double u,double v) {}

    public ObjItemMesh { triangles=List.copyOf(triangles); }

    static ObjItemMesh load(final ResourceDocument declaration,final com.fasterxml.jackson.databind.JsonNode json,
            final ResourceAssetCatalog assets) {
        try {
            final var model=ResourceLocation.parse(json.path("model").asText());
            if(!model.namespace().equals(declaration.source().internalPath().split("/")[1])
                    || !model.path().startsWith("models/") || !model.path().endsWith(".obj"))
                throw new IOException("OBJ must be in its declaring asset namespace");
            final var textures=assets.texture(json.path("textures").path("default").asText());
            if(textures.size()!=1)throw new IOException("OBJ requires one resolved default texture");
            final String path="assets/"+model.namespace()+"/"+model.path();
            final var text=read(declaration.source().container(),path,256*1024);
            final var points=new ArrayList<BlockGeometryModel.Vector>();
            final var uvs=new ArrayList<UV>();
            final var triangles=new ArrayList<Triangle>();
            final var materials=new HashSet<String>();
            String library=null;
            for(final String line:text.lines().toList()){
                final var fields=line.split("#",2)[0].strip().split("\\s+");
                switch(fields[0]){
                    case "v" -> points.add(new BlockGeometryModel.Vector(number(fields[1],-4,4),number(fields[2],-4,4),number(fields[3],-4,4)));
                    case "vt" -> {
                        final double v=number(fields[2],0,1);
                        uvs.add(new UV(number(fields[1],0,1),json.path("flip_v").asBoolean()?1-v:v));
                    }
                    case "mtllib" -> {
                        if(library!=null||fields.length!=2||!fields[1].matches("[A-Za-z0-9_.-]+\\.mtl"))
                            throw new IOException("Unsupported OBJ material library");
                        library=fields[1];
                    }
                    case "usemtl" -> materials.add(fields[1]);
                    case "f" -> {
                        if(fields.length<4||fields.length>5)throw new IOException("Only OBJ triangles and quads are supported");
                        final var first=vertex(fields[1],points,uvs);
                        for(int i=2;i<fields.length-1;i++)
                            triangles.add(new Triangle(first,vertex(fields[i],points,uvs),vertex(fields[i+1],points,uvs)));
                    }
                    default -> { }
                }
                if(points.size()>4096||uvs.size()>8192||triangles.size()>4096)throw new IOException("OBJ exceeds geometry limits");
            }
            if(library==null||materials.size()!=1||triangles.isEmpty())throw new IOException("Expected a single textured OBJ material");
            final var mtl=read(declaration.source().container(),path.substring(0,path.lastIndexOf('/')+1)+library,16*1024);
            final var names=mtl.lines().map(String::strip).filter(line->line.startsWith("newmtl ")).map(line->line.substring(7).strip()).toList();
            final var maps=mtl.lines().map(String::strip).filter(line->line.startsWith("map_Kd ")).map(line->line.substring(7).strip()).toList();
            if(names.size()!=1||!materials.contains(names.getFirst())
                    || !maps.equals(List.of(json.path("textures").path("default").asText())))
                throw new IOException("OBJ material does not match the declared texture");
            return new ObjItemMesh(textures.getFirst(),triangles);
        } catch(IOException|RuntimeException error) {
            throw new BlockModelException(BlockModelException.Code.UNSUPPORTED_RENDERER,"OBJ: "+error.getMessage());
        }
    }

    private static Vertex vertex(final String field,final List<BlockGeometryModel.Vector> points,final List<UV> uvs){
        final var indices=field.split("/");
        final var point=points.get(Integer.parseInt(indices[0])-1);
        final var uv=uvs.get(Integer.parseInt(indices[1])-1);
        return new Vertex(point.x(),point.y(),point.z(),uv.u(),uv.v());
    }
    private static double number(final String value,final double min,final double max)throws IOException{
        final double n=Double.parseDouble(value);
        if(!Double.isFinite(n)||n<min||n>max)throw new IOException("OBJ coordinate outside supported range: "+value);
        return n;
    }
    private static String read(final Path container,final String path,final int limit)throws IOException{
        if(Files.isDirectory(container))return new String(SafeFiles.read(container,container.resolve(path),limit),StandardCharsets.UTF_8);
        try(final var zip=new ZipFile(container.toFile())){
            final var entry=zip.getEntry(path);
            if(entry==null||entry.getSize()>limit)throw new IOException("OBJ asset unavailable: "+path);
            try(final var stream=zip.getInputStream(entry)){return new String(SafeFiles.read(stream,limit),StandardCharsets.UTF_8);}
        }
    }
}
