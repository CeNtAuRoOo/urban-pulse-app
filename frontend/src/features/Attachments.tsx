import type { TemporaryFile } from "../services/temporary-file";
import { useUnsavedChanges } from "../services/form-guard";
import { useEffect, useRef, useState } from "react";
import { Image, Platform, View } from "react-native";
import * as DocumentPicker from "expo-document-picker";
import {
  Body,
  Button,
  Confirm,
  Details,
  Feedback,
  Field,
  Panel,
  QueryState,
  Row,
  date,
  styles,
} from "../components/ui";
import { useAction, useResource } from "../services/hooks";
import { useSession } from "../services/session";
import { permissions } from "../services/permissions";
import { textLength } from "../services/validation";
import { imageUrl, releaseImage, saveImage, pickerTemporaryFile } from "../platform/files";
import type { Incident } from "../types/api";
import type { Attachment } from "../types/operations";
export function Attachments({ incident }: { incident: Incident }) {
  const { api, session } = useSession(),
    action = useAction(),
    list = useResource<Attachment[]>(`/incidents/${incident.id}/attachments`);
  const writable = permissions(session?.user, incident).attachments;
  const [file, setFile] = useState<DocumentPicker.DocumentPickerAsset>(),
    [description, setDescription] = useState("");
  const guard = useUnsavedChanges(!!file || !!description);
  const temporary = useRef<TemporaryFile | null>(null),
    live = useRef(true);
  const clearFile = () => {
    const owned = temporary.current;
    temporary.current = null;
    void owned?.dispose();
    setFile(undefined);
  };
  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
      void temporary.current?.dispose();
      temporary.current = null;
    };
  }, []);
  return (
    <Panel title="Evidencias privadas">
      <Body muted>
        Fotografías JPEG o PNG. Hasta 5 archivos, 10 MiB por archivo, 16 millones de píxeles y 8192 píxeles
        por lado. Solo las personas autorizadas pueden verlas.
      </Body>
      <QueryState {...list} empty={list.result?.data.length === 0} />
      <Feedback error={action.error} success={action.success} />
      {writable ? (
        <>
          <Button
            secondary
            disabled={action.busy || (list.result?.data.length || 0) >= 5}
            icon="paperclip"
            onPress={() =>
              void action.run(async () => {
                const result = await DocumentPicker.getDocumentAsync({
                  type: ["image/jpeg", "image/png"],
                  multiple: false,
                  copyToCacheDirectory: true,
                });
                if (result.canceled) return;
                const chosen = result.assets[0];
                const owned = pickerTemporaryFile(chosen.uri);
                try {
                  if (!live.current) {
                    await owned.dispose();
                    return;
                  }
                  if (!chosen.size || chosen.size > 10485760)
                    throw new Error("La fotografía debe ocupar entre 1 byte y 10 MiB.");
                  if (chosen.mimeType && !["image/jpeg", "image/png"].includes(chosen.mimeType))
                    throw new Error("Selecciona un JPEG o PNG.");
                  const size = await new Promise<{
                    width: number;
                    height: number;
                  }>((resolve, reject) =>
                    Image.getSize(
                      chosen.uri,
                      (width, height) => resolve({ width, height }),
                      () => reject(new Error("No se pudo leer la fotografía."))
                    )
                  );
                  if (size.width > 8192 || size.height > 8192 || size.width * size.height > 16000000)
                    throw new Error("La imagen supera las dimensiones permitidas.");
                  if (!live.current) {
                    await owned.dispose();
                    return;
                  }
                  const previous = temporary.current;
                  temporary.current = owned;
                  setFile(chosen);
                  await previous?.dispose();
                } catch (error) {
                  await owned.dispose();
                  throw error;
                }
              })
            }
          >
            Seleccionar fotografía
          </Button>
          {file ? (
            <>
              <Body>
                {file.name} · {new Intl.NumberFormat("es-ES").format(file.size!)} bytes
              </Body>
              <Field
                label="Descripción de la evidencia (opcional)"
                value={description}
                onChangeText={setDescription}
                multiline
              />
              <Button
                disabled={action.busy}
                onPress={() =>
                  void action.run(async () => {
                    const owned = temporary.current;
                    if (!owned) return;
                    await owned.use(async () => {
                      const form = new FormData();
                      if (Platform.OS === "web") {
                        const blob = file.file || (await (await fetch(file.uri)).blob());
                        form.append("file", blob, file.name);
                      } else
                        form.append("file", {
                          uri: file.uri,
                          name: file.name,
                          type: file.mimeType || "image/jpeg",
                        } as unknown as Blob);
                      if (description)
                        form.append("description", textLength(description, 0, 300, "Descripción"));
                      await api(`/incidents/${incident.id}/attachments`, {
                        method: "POST",
                        body: form,
                      });
                    });
                    guard.saved();
                    clearFile();
                    setDescription("");
                    list.reload();
                    action.setSuccess("Evidencia incorporada.");
                  })
                }
              >
                {action.busy ? "Subiendo…" : "Subir fotografía"}
              </Button>
              <Button
                secondary
                disabled={action.busy}
                onPress={() =>
                  guard.discard(() => {
                    clearFile();
                    setDescription("");
                  })
                }
              >
                Cancelar evidencia
              </Button>
            </>
          ) : null}
        </>
      ) : (
        <Body muted>El estado actual del expediente no permite añadir ni eliminar evidencias.</Body>
      )}
      {list.result?.data.map((a) => (
        <AttachmentCard key={a.id} item={a} writable={writable} changed={list.reload} />
      ))}
    </Panel>
  );
}
function AttachmentCard({
  item,
  writable,
  changed,
}: {
  item: Attachment;
  writable: boolean;
  changed: () => void;
}) {
  const { api } = useSession(),
    action = useAction();
  const [uri, setUri] = useState(""),
    [detail, setDetail] = useState(false);
  const resource = useResource<Attachment>(
    detail ? `/incidents/${item.incidentId}/attachments/${item.id}` : null
  );
  const current = useRef(""),
    live = useRef(true);
  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
      if (current.current) releaseImage(current.current);
    };
  }, []);
  const path = `/incidents/${item.incidentId}/attachments/${item.id}`;
  return (
    <View style={styles.divider}>
      <Body>{item.originalFilename}</Body>
      <Body muted>
        {date(item.createdAt)} ·{" "}
        {new Intl.NumberFormat("es-ES", { maximumFractionDigits: 2 }).format(item.sizeBytes / 1048576)} MiB
      </Body>
      {item.description ? <Body>{item.description}</Body> : null}
      <Feedback error={action.error} />
      {uri ? (
        <Image
          accessibilityLabel={item.description || item.originalFilename}
          source={{ uri }}
          resizeMode="contain"
          style={{ width: "100%", height: 280 }}
        />
      ) : null}
      <Row>
        <Button
          secondary
          disabled={action.busy}
          onPress={() =>
            void action.run(async () => {
              if (uri) {
                releaseImage(uri);
                current.current = "";
                setUri("");
                return;
              }
              const r = await api<Blob>(`${path}/content`, {
                responseType: "blob",
              });
              const url = await imageUrl(r.data);
              if (!live.current) {
                releaseImage(url);
                return;
              }
              current.current = url;
              setUri(url);
            })
          }
        >
          {uri ? "Ocultar imagen" : "Ver imagen"}
        </Button>
        <Button
          secondary
          disabled={action.busy}
          onPress={() =>
            void action.run(async () => {
              const r = await api<Blob>(`${path}/content`, {
                responseType: "blob",
              });
              await saveImage(r.data, item.originalFilename);
            })
          }
        >
          Descargar
        </Button>
        <Button secondary onPress={() => setDetail(!detail)}>
          Metadatos
        </Button>
        {writable ? (
          <Confirm
            title="Eliminar evidencia"
            message="La fotografía dejará de estar disponible en el expediente. Esta operación no se puede deshacer desde la aplicación."
            danger
            disabled={action.busy}
            onConfirm={() =>
              void action.run(async () => {
                await api(path, { method: "DELETE" });
                changed();
              })
            }
          >
            Eliminar
          </Confirm>
        ) : null}
      </Row>
      {detail ? (
        <>
          <QueryState {...resource} />
          {resource.result ? (
            <Details
              entries={[
                ["Nombre", resource.result.data.originalFilename],
                ["Formato", resource.result.data.contentType],
                ["Tamaño", `${resource.result.data.sizeBytes} bytes`],
                ["Fecha", date(resource.result.data.createdAt)],
                ["Descripción", resource.result.data.description],
              ]}
            />
          ) : null}
        </>
      ) : null}
    </View>
  );
}
