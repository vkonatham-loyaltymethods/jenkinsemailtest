package com.rcx.events.kafka.serializer;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import org.bson.BsonTimestamp;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcx.events.mongo.model.OplogDocument;
import net.minidev.json.parser.ParseException;

@RunWith(SpringRunner.class)
public class OplogDocumentSerializerTest {

  @InjectMocks
  private OplogDocumentSerializer oplogDocumentSerializer;

  @Before
  public void setUp() {
    oplogDocumentSerializer.configure(null, false);
  }

  @After
  public void close() {
    oplogDocumentSerializer.close();
  }

  @Test
  public void TestObjectIdSerialize() throws ParseException {
    OplogDocument odoc = new OplogDocument();
    odoc.setOp("i");
    odoc.setNs("fortest.coll1");
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("_id", new ObjectId("5c46e2367b2bfe0102059ff4"));
    odoc.setO(map);

    byte[] oplogDoc = oplogDocumentSerializer.serialize(null, odoc);

    ObjectMapper mapper = new ObjectMapper();
    try {
      map = mapper.readValue(new String(oplogDoc), Map.class);
      Map<String, Object> oMap = (Map<String, Object>) map.get("o");
      String _id = (String) oMap.get("_id");
      Assert.assertTrue("Oplog Doc ObjectId value should be string", _id instanceof String);
      Assert.assertTrue("Oplog Doc ObjectId value should same as oplogDoc instance value",
          "5c46e2367b2bfe0102059ff4".equals(_id));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void TestBsonTimestampSerialize() throws ParseException, java.text.ParseException {
    OplogDocument odoc = new OplogDocument();
    odoc.setOp("i");
    odoc.setNs("fortest.coll1");
    SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    Date date = format.parse("2019/10/10 01:01:00");
    BsonTimestamp bts = new BsonTimestamp((int) (date.getTime() / 1000), 0);
    odoc.setTs(bts);

    byte[] oplogDoc = oplogDocumentSerializer.serialize(null, odoc);

    ObjectMapper mapper = new ObjectMapper();
    try {
      Map<String, Object> map = mapper.readValue(new String(oplogDoc), Map.class);
      String ts = (String) map.get("ts");
      Assert.assertTrue("Oplog Doc BsonTimestamp value should be string", ts instanceof String);
      Assert.assertTrue("Oplog Doc BsonTimestamp value should same as oplogDocument instance value",
          "6745973104532520960".equals(ts));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void TestDateSerialize() throws ParseException, java.text.ParseException {
    OplogDocument odoc = new OplogDocument();
    odoc.setOp("i");
    odoc.setNs("fortest.coll1");
    SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    Date date = format.parse("2019/10/10 01:01:00");
    odoc.setWall(date);

    byte[] oplogDoc = oplogDocumentSerializer.serialize(null, odoc);

    ObjectMapper mapper = new ObjectMapper();
    try {
      Map<String, Object> map = mapper.readValue(new String(oplogDoc), Map.class);
      String wall = (String) map.get("wall");
      Assert.assertTrue("Oplog Doc Date value should be string", wall instanceof String);
      Assert.assertTrue("Oplog Doc Date value should same as oplogDocument instance value",
          "2019-10-10T01:01:00.000Z".equals(wall));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
