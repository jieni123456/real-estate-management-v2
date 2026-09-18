package service;

import dao.HouseDAO;
import model.House;
import model.Landlord;

import java.util.List;

public class HouseService {

    private final HouseDAO houseDAO = new HouseDAO();

    public boolean insertHouse(House house) {
        System.out.println("新增房屋: " + house.getId());
        return houseDAO.insertHouse(house);
    }

    public boolean updateHouse(House house) {
        System.out.println("更新房屋: " + house.getId());
        return houseDAO.updateHouse(house);
    }

    public boolean existsHouse(String houseId) {
        return houseDAO.exists(houseId);
    }

    public boolean existsLandlord(String landlordId) {
        return houseDAO.landlordExists(landlordId);
    }

    /**
     * 房屋当前状态；房屋不存在时返回 {@code null}。
     * 供「已租出的房子不能再登记带看」这条规则判断（G-020）。
     */
    public String getHouseStatus(String houseId) {
        return houseDAO.findStatus(houseId);
    }

    public List<House> getAllHouses() {
        System.out.println("从DAO获取所有房屋");
        return houseDAO.getAllHouses();
    }

    /** 房东下拉的数据来源（G-007） */
    public List<Landlord> getAllLandlords() {
        System.out.println("从DAO获取所有房东");
        return houseDAO.getAllLandlords();
    }

    public boolean deleteHouse(String houseId) {
        System.out.println("删除房屋: " + houseId);
        return houseDAO.deleteHouse(houseId);
    }

    /** 该房东名下的房屋数量，供界面预告删除会连带清理房东（G-018） */
    public int countHousesByLandlord(String landlordId) {
        return houseDAO.countHousesByLandlord(landlordId);
    }
}
